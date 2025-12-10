package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.AssociativeAddressTrie;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnalyseRuleUnTrustVoteServiceImpl extends AbstractAnalyseRuleServiceImpl {
    @Value("${sparkle.analyse.untrusted-vote.duration}")
    private long duration;
    @Value("${sparkle.analyse.untrusted-vote.include-modules}")
    private String untrustedVoteIncludeModules;
    @Value("${sparkle.analyse.untrusted-vote.ipv4.enable}")
    private boolean useIPv4;
    @Value("${sparkle.analyse.untrusted-vote.ipv4.min-userapps-vote}")
    private int ipv4MinUserAppsVote;
    @Value("${sparkle.analyse.untrusted-vote.ipv4.min-ban-count}")
    private int ipv4MinBanCountVote;
    @Value("${sparkle.analyse.untrusted-vote.ipv6.enable}")
    private boolean useIPv6;
    @Value("${sparkle.analyse.untrusted-vote.ipv6.min-userapps-vote}")
    private int ipv6MinUserAppsVote;
    @Value("${sparkle.analyse.untrusted-vote.ipv6.min-ban-count}")
    private int ipv6MinBanCountVote;

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "${sparkle.analyse.untrusted-vote.schedule}")
    public void analyseUntrusted() {
        List<GeneratedRule> rules = new ArrayList<>();
        List<GeneratedRule> resultList = this.baseMapper.analyseByModule(
                        OffsetDateTime.now().minus(System.currentTimeMillis() - duration, ChronoUnit.MILLIS),
                        List.of(untrustedVoteIncludeModules.split(",")))
                .stream()
                .map(analysis -> {
                    IPAddress ip = IPAddressUtil.getIPAddress(analysis.getPeerIpCidr());
                    return new GeneratedRule(ip, analysis.getBanCount(), analysis.getUserappsCount(), 0);
                }).collect(Collectors.toCollection(ArrayList::new));
        log.info("从数据库查询到 {} 条记录", resultList.size());
        DualIPv4v6AssociativeTries<GeneratedRule> tries = new DualIPv4v6AssociativeTries<>();
        for (GeneratedRule result : resultList) {
            IPAddress ipAddress = result.getPeerIpCidr();
            tries.put(ipAddress, result);
        }
        IPv4Address[] ipv4Prefixes = mergeIpsV4(tries.getIPv4Trie());
        IPv6Address[] ipv6Prefixes = mergeIpsV6(tries.getIPv6Trie());
        log.info("IPv4 合并后生成 {} 个前缀块", ipv4Prefixes.length);
        log.info("IPv6 合并后生成 {} 个前缀块", ipv6Prefixes.length);
        for (IPv4Address prefix : ipv4Prefixes) {
            log.info("IPv4 合并块: {}", prefix);
        }
        for (IPv6Address prefix : ipv6Prefixes) {
            log.info("IPv6 合并块: {}", prefix);
        }
        triesMergeV4(tries.getIPv4Trie(), ipv4Prefixes);
        triesMergeV6(tries.getIPv6Trie(), ipv6Prefixes);
        // 此时 Tries 就是已经合并好的结果，过滤一下规则
        tries.nodeIterator(false).forEachRemaining(node -> {
            var ip = node.getKey();
            var rule = node.getValue();
            if (ip.isIPv4()) {
                if (useIPv4 && rule.getUserappsCount() >= ipv4MinUserAppsVote && rule.getBanCount() >= ipv4MinBanCountVote) {
                    rules.add(rule);
                }
            } else {
                if (useIPv6 && rule.getUserappsCount() >= ipv6MinUserAppsVote && rule.getBanCount() >= ipv6MinBanCountVote) {
                    rules.add(rule);
                }
            }
        });

        log.info("过滤后生成 {} 条规则", rules.size());
        StringBuilder sb = new StringBuilder();
        for (GeneratedRule rule : rules) {
            // 过滤掉无效的 IP 地址（0.0.0.0 或全 0 的 IPv6）
            IPAddress ipAddr = rule.getPeerIpCidr();
            Integer prefixLength = ipAddr.getPrefixLength();

            // 检查是否为无效的全 0 地址或过大的 CIDR 块
            if (ipAddr.isZero() || (prefixLength != null && prefixLength == 0)) {
                log.warn("跳过无效的 IP 地址: {}, 前缀长度: {}", ipAddr, prefixLength);
                continue;
            }

            sb.append("# [Sparkle3 不受信任投票] 封禁计数: ").append(rule.getBanCount())
                    .append(", 不信任票数: ").append(rule.getUserappsCount())
                    .append(", 合并记录数量: ").append(rule.getMergedRecords())
                    .append("\n");
            if ((rule.getPeerIpCidr().isIPv4() && rule.getPeerIpCidr().getPrefixLength() == 32)
                    || (rule.getPeerIpCidr().isIPv6() && rule.getPeerIpCidr().getPrefixLength() == 128)) {
                rule.setPeerIpCidr(rule.getPeerIpCidr().withoutPrefixLength());
            }
            String outputIp = rule.getPeerIpCidr().toZeroHost().toNormalizedString();
            log.info("规则: {} -> 输出: {}", rule.getPeerIpCidr(), outputIp);
            sb.append(outputIp).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey(), sb.toString());
        //noinspection UnstableApiUsage
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION.getKey());
        return Pair.of(version, value);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class GeneratedRule {
        private IPAddress peerIpCidr;
        private long banCount;
        private long userappsCount;
        private long mergedRecords;
    }

    private void triesMergeV6(AssociativeAddressTrie<IPv6Address, GeneratedRule> trie, IPv6Address[] prefixes) {
        for (IPv6Address prefix : prefixes) {
            GeneratedRule rule = new GeneratedRule();
            long totalBanCount = 0;
            long totalUserappsCount = 0;
            long totalMergedRecords = 0;
            var it = trie.elementsContainedBy(prefix).nodeIterator(false);
            while (it.hasNext()) {
                var node = it.next();
                GeneratedRule r = node.getValue();
                totalBanCount += r.getBanCount();
                totalUserappsCount += r.getUserappsCount();
                totalMergedRecords += 1;
            }
            rule.setPeerIpCidr(prefix);
            rule.setBanCount(totalBanCount);
            rule.setUserappsCount(totalUserappsCount);
            rule.setMergedRecords(totalMergedRecords);
            trie.removeElementsContainedBy(prefix);
            trie.put(prefix, rule);
        }
    }

    private void triesMergeV4(AssociativeAddressTrie<IPv4Address, GeneratedRule> trie, IPv4Address[] prefixes) {
        for (IPv4Address prefix : prefixes) {
            GeneratedRule rule = new GeneratedRule();
            long totalBanCount = 0;
            long totalUserappsCount = 0;
            long totalMergedRecords = 0;
            var it = trie.elementsContainedBy(prefix).nodeIterator(false);
            while (it.hasNext()) {
                var node = it.next();
                GeneratedRule r = node.getValue();
                totalBanCount += r.getBanCount();
                totalUserappsCount += r.getUserappsCount();
                totalMergedRecords += 1;
            }
            rule.setPeerIpCidr(prefix);
            rule.setBanCount(totalBanCount);
            rule.setUserappsCount(totalUserappsCount);
            rule.setMergedRecords(totalMergedRecords);
            trie.removeElementsContainedBy(prefix);
            trie.put(prefix, rule);
        }
    }

    @SuppressWarnings("unchecked")
    private IPv4Address[] mergeIpsV4(AssociativeAddressTrie<IPv4Address, GeneratedRule> trie) {
        var firstAddedNode = trie.firstAddedNode();
        if (firstAddedNode == null) return new IPv4Address[0];
        var it = trie.nodeIterator(false);
        List<IPv4Address> ips = new ArrayList<>();
        while (it.hasNext()) {
            var node = it.next();
            ips.add(node.getKey());
        }
        IPv4Address[] array = new IPv4Address[ips.size()];
        return firstAddedNode.getKey().mergeToPrefixBlocks(ips.toArray(array));
    }

    @SuppressWarnings("unchecked")
    private IPv6Address[] mergeIpsV6(AssociativeAddressTrie<IPv6Address, GeneratedRule> trie) {
        var firstAddedNode = trie.firstAddedNode();
        if (firstAddedNode == null) return new IPv6Address[0];
        var it = trie.nodeIterator(false);
        List<IPv6Address> ips = new ArrayList<>();
        while (it.hasNext()) {
            var node = it.next();
            ips.add(node.getKey());
        }
        IPv6Address[] array = new IPv6Address[ips.size()];
        return firstAddedNode.getKey().mergeToPrefixBlocks(ips.toArray(array));
    }

}
