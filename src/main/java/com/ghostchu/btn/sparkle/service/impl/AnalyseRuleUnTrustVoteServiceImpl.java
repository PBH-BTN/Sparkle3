package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseByModuleResult;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.MsgUtil;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AnalyseRuleUnTrustVoteServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPDenyListRuleProvider {
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
    @Transactional
    public void analyseUntrusted() {
        DualIPv4v6AssociativeTries<GeneratedRule> tries = new DualIPv4v6AssociativeTries<>();
        log.info("Performing untrusted vote analysis with duration: {} ms, includeModules: {}, useIPv4: {}, ipv4MinUserAppsVote: {}, ipv4MinBanCountVote: {}, useIPv6: {}, ipv6MinUserAppsVote: {}, ipv6MinBanCountVote: {}",
                duration, untrustedVoteIncludeModules, useIPv4, ipv4MinUserAppsVote, ipv4MinBanCountVote, useIPv6, ipv6MinUserAppsVote, ipv6MinBanCountVote);
        // 在 try-with-resources 块内完成所有 Cursor 操作
        try (var cursor = this.baseMapper.analyseByModule(
                OffsetDateTime.now().minus(System.currentTimeMillis() - duration, ChronoUnit.MILLIS),
                List.of(untrustedVoteIncludeModules.split(",")))) {
            // 必须在 try 块内完成迭代，因为 Cursor 离开 try 块后会自动关闭
            for (AnalyseByModuleResult analysis : cursor) {
                IPAddress ip = IPAddressUtil.getIPAddress(analysis.getPeerIpCidr());
                if (ip.isIPv6()) {
                    ip = IPAddressUtil.toPrefixBlockAndZeroHost(ip, 56);
                }
                // 直接检查是否存在，如果存在则合并数据
                GeneratedRule existingRule = tries.get(ip);
                if (existingRule != null) {
                    // 合并规则数据
                    existingRule.setBanCount(existingRule.getBanCount() + analysis.getBanCount());
                    existingRule.setUserappsCount(existingRule.getUserappsCount() + analysis.getUserappsCount());
                    existingRule.setToPeerTraffic(existingRule.getToPeerTraffic() + analysis.getToPeerTraffic());
                    existingRule.setFromPeerTraffic(existingRule.getFromPeerTraffic() + analysis.getFromPeerTraffic());
                } else {
                    // 新规则
                    GeneratedRule rule = new GeneratedRule(ip, analysis.getBanCount(), analysis.getUserappsCount(), 0, analysis.getToPeerTraffic(), analysis.getFromPeerTraffic());
                    tries.put(ip, rule);
                }
            }
        } catch (Exception e) {
            log.error("Error processing analyseByModule cursor", e);
            return;
        }

        // 执行 IP 合并
        IPv4Address[] ipv4Prefixes = mergeIpsV4(tries.getIPv4Trie());
        IPv6Address[] ipv6Prefixes = mergeIpsV6(tries.getIPv6Trie());
        triesMergeV4(tries.getIPv4Trie(), ipv4Prefixes);
        triesMergeV6(tries.getIPv6Trie(), ipv6Prefixes);

        // 构建最终结果，边遍历边过滤边构建输出
        StringBuilder sb = new StringBuilder();
        tries.nodeIterator(false).forEachRemaining(node -> {
            var ip = node.getKey();
            var rule = node.getValue();
            boolean shouldInclude = false;

            if (ip.isIPv4()) {
                if (useIPv4 && rule.getUserappsCount() >= ipv4MinUserAppsVote && rule.getBanCount() >= ipv4MinBanCountVote) {
                    shouldInclude = true;
                }
            } else {
                if (useIPv6 && rule.getUserappsCount() >= ipv6MinUserAppsVote && rule.getBanCount() >= ipv6MinBanCountVote) {
                    shouldInclude = true;
                }
            }

            if (shouldInclude) {
                // 过滤掉无效的 IP 地址（0.0.0.0 或全 0 的 IPv6）
                IPAddress ipAddr = rule.getPeerIpCidr();
                Integer prefixLength = ipAddr.getPrefixLength();

                // 检查是否为无效的全 0 地址或过大的 CIDR 块
                if (ipAddr.isZero() || (prefixLength != null && prefixLength == 0)) {
                    return;
                }

                sb.append("# [Sparkle3 不受信任投票] 封禁计数: ").append(rule.getBanCount())
                        .append(", 不信任票数: ").append(rule.getUserappsCount())
                        .append(", 合并记录数量: ").append(rule.getMergedRecords())
                        .append(", BTN网络发送到此Peer流量: ").append(MsgUtil.humanReadableByteCountBin(rule.getToPeerTraffic()))
                        .append(", BTN网络从此Peer获取流量: ").append(MsgUtil.humanReadableByteCountBin(rule.getFromPeerTraffic()))
                        .append("\n");

                // 先调用 toZeroHost()，然后再决定是否移除前缀长度
                IPAddress outputAddr = rule.getPeerIpCidr().toZeroHost();
                if ((outputAddr.isIPv4() && outputAddr.getPrefixLength() == 32)
                        || (outputAddr.isIPv6() && outputAddr.getPrefixLength() == 128)) {
                    outputAddr = outputAddr.withoutPrefixLength();
                }
                String outputIp = outputAddr.toNormalizedString();
                sb.append(outputIp).append("\n");
            }
        });

        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey(), sb.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
        log.info("Untrusted vote analysis completed. Generated content length: {}, version: {}", sb.length(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey());
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class GeneratedRule {
        private IPAddress peerIpCidr;
        private long banCount;
        private long userappsCount;
        private long mergedRecords;
        private long toPeerTraffic;
        private long fromPeerTraffic;
    }

    private void triesMergeV6(AssociativeAddressTrie<IPv6Address, GeneratedRule> trie, IPv6Address[] prefixes) {
        for (IPv6Address prefix : prefixes) {
            GeneratedRule rule = new GeneratedRule();
            long totalBanCount = 0;
            long totalUserappsCount = 0;
            long totalMergedRecords = 0;
            long totalToPeerTraffic = 0;
            long totalFromPeerTraffic = 0;
            var it = trie.elementsContainedBy(prefix).nodeIterator(false);
            while (it.hasNext()) {
                var node = it.next();
                GeneratedRule r = node.getValue();
                totalBanCount += r.getBanCount();
                totalUserappsCount += r.getUserappsCount();
                totalMergedRecords += 1;
                totalToPeerTraffic += r.getToPeerTraffic();
                totalFromPeerTraffic += r.getFromPeerTraffic();
            }
            rule.setPeerIpCidr(prefix);
            rule.setBanCount(totalBanCount);
            rule.setUserappsCount(totalUserappsCount);
            rule.setMergedRecords(totalMergedRecords);
            rule.setToPeerTraffic(totalToPeerTraffic);
            rule.setFromPeerTraffic(totalFromPeerTraffic);
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
            long totalToPeerTraffic = 0;
            long totalFromPeerTraffic = 0;
            var it = trie.elementsContainedBy(prefix).nodeIterator(false);
            while (it.hasNext()) {
                var node = it.next();
                GeneratedRule r = node.getValue();
                totalBanCount += r.getBanCount();
                totalUserappsCount += r.getUserappsCount();
                totalMergedRecords += 1;
                totalToPeerTraffic += r.getToPeerTraffic();
                totalFromPeerTraffic += r.getFromPeerTraffic();
            }
            rule.setPeerIpCidr(prefix);
            rule.setBanCount(totalBanCount);
            rule.setUserappsCount(totalUserappsCount);
            rule.setMergedRecords(totalMergedRecords);
            rule.setToPeerTraffic(totalToPeerTraffic);
            rule.setFromPeerTraffic(totalFromPeerTraffic);
            trie.removeElementsContainedBy(prefix);
            trie.put(prefix, rule);
        }
    }

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
