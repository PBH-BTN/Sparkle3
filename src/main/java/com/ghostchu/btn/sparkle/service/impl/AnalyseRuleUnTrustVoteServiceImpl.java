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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyseRuleUnTrustVoteServiceImpl extends AbstractAnalyseRuleServiceImpl {
    @Value("${sparkle.analyse.untrusted-vote.duration}")
    private long duration;
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
        List<GeneratedRule> resultList = this.baseMapper.analyseByModule(new Timestamp(System.currentTimeMillis() - duration))
                .stream()
                .map(analysis -> {
                    IPAddress ip = IPAddressUtil.getIPAddress(analysis.getPeerIpCidr());
                    return new GeneratedRule(ip, analysis.getBanCount(), analysis.getUserappsCount(), 0);
                }).collect(Collectors.toCollection(ArrayList::new));
        DualIPv4v6AssociativeTries<GeneratedRule> tries = new DualIPv4v6AssociativeTries<>();
        for (GeneratedRule result : resultList) {
            IPAddress ipAddress = result.getPeerIpCidr();
            tries.put(ipAddress, result);
        }
        IPAddress[] ipv4Prefixes = mergeIps(tries.getIPv4Trie());
        IPAddress[] ipv6Prefixes = mergeIps(tries.getIPv6Trie());
        triesMerge(tries.getIPv4Trie(), (IPv4Address[]) ipv4Prefixes);
        triesMerge(tries.getIPv6Trie(), (IPv6Address[]) ipv6Prefixes);
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

        StringBuilder sb = new StringBuilder();
        for (GeneratedRule rule : rules) {
            sb.append("# [Sparkle3 不受信任投票] 封禁计数: ").append(rule.getBanCount())
                    .append(", 不信任票数: ").append(rule.getUserappsCount())
                    .append(", 合并记录数量: ").append(rule.getMergedRecords())
                    .append("\n");
            if ((rule.getPeerIpCidr().isIPv4() && rule.getPeerIpCidr().getPrefixLength() == 32)
                    || (rule.getPeerIpCidr().isIPv6() && rule.getPeerIpCidr().getPrefixLength() == 128)) {
                rule.setPeerIpCidr(rule.getPeerIpCidr().withoutPrefixLength());
            }
            sb.append(rule.getPeerIpCidr().toZeroHost().toNormalizedString()).append("\n");
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VALUE.getKey(), sb.toString());
        //noinspection UnstableApiUsage
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_UNTRUSTED_VOTE_VERSION.getKey(), Hashing.crc32c().hashString(sb.toString(), StandardCharsets.UTF_8).toString());
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

    private <T extends IPAddress> void triesMerge(AssociativeAddressTrie<T, GeneratedRule> trie, T[] prefixes) {
        for (T prefix : prefixes) {
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


    private <T extends IPAddress> IPAddress[] mergeIps(AssociativeAddressTrie<T, GeneratedRule> iPv4Trie) {
        var firstAddedNode = iPv4Trie.firstAddedNode();
        if (firstAddedNode == null) return new IPAddress[0];
        var it = iPv4Trie.nodeIterator(false);
        List<IPAddress> ips = new ArrayList<>();
        while (it.hasNext()) {
            var node = it.next();
            ips.add(node.getKey());
        }
        return firstAddedNode.getKey().mergeToPrefixBlocks(ips.toArray(new IPAddress[0]));
    }

}
