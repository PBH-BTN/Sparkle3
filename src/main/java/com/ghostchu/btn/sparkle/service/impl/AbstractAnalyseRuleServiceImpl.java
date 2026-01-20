package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.entity.AnalyseRule;
import com.ghostchu.btn.sparkle.mapper.AnalyseRuleMapper;
import com.ghostchu.btn.sparkle.service.IAnalyseRuleService;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.AssociativeAddressTrie;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractAnalyseRuleServiceImpl extends ServiceImpl<AnalyseRuleMapper, AnalyseRule> implements IAnalyseRuleService {

    public AbstractAnalyseRuleServiceImpl() {
    }

    public abstract Pair<@Nullable String, @Nullable String> getGeneratedContent();

    <T> void mergeIps(@NotNull DualIPv4v6AssociativeTries<T> tries) {
        IPv4Address[] ipv4Prefixes = commonMergeIpsV4(tries.getIPv4Trie());
        IPv6Address[] ipv6Prefixes = commonMergeIpsV6(tries.getIPv6Trie());
        commonTriesMergeV4(tries.getIPv4Trie(), ipv4Prefixes);
        commonTriesMergeV6(tries.getIPv6Trie(), ipv6Prefixes);
    }

    @NotNull
    <T> Map<IPAddress, T> formatAndIterateIp(@NotNull DualIPv4v6AssociativeTries<T> tries) {
        Map<IPAddress, T> map = new LinkedHashMap<>();
        tries.nodeIterator(false).forEachRemaining(node -> {
            IPAddress outputAddr = node.getKey();
            if(outputAddr.getPrefixLength() != null) {
                if ((outputAddr.isIPv4() && outputAddr.getPrefixLength() == 32) || (outputAddr.isIPv6() && outputAddr.getPrefixLength() == 128)) {
                    outputAddr = outputAddr.withoutPrefixLength();
                }
            }
            map.put(outputAddr, node.getValue());
        });
        return map;
    }


    private <T> void commonTriesMergeV6(@NotNull AssociativeAddressTrie<IPv6Address, T> trie, @NotNull IPv6Address[] prefixes) {
        for (IPv6Address prefix : prefixes) {
            // 收集该前缀下的第一个值作为聚合结果
            T aggregatedValue = null;
            var it = trie.elementsContainedBy(prefix).nodeIterator(false);
            if (it.hasNext()) {
                aggregatedValue = it.next().getValue();
            }

            // 删除所有被该前缀包含的元素
            trie.removeElementsContainedBy(prefix);

            // 将聚合后的前缀块添加回trie
            if (aggregatedValue != null) {
                trie.put(prefix, aggregatedValue);
            }
        }
    }

    private <T> void commonTriesMergeV4(@NotNull AssociativeAddressTrie<IPv4Address, T> trie, @NotNull IPv4Address[] prefixes) {
        for (IPv4Address prefix : prefixes) {
            // 收集该前缀下的第一个值作为聚合结果
            T aggregatedValue = null;
            var it = trie.elementsContainedBy(prefix).nodeIterator(false);
            if (it.hasNext()) {
                aggregatedValue = it.next().getValue();
            }

            // 删除所有被该前缀包含的元素
            trie.removeElementsContainedBy(prefix);

            // 将聚合后的前缀块添加回trie
            if (aggregatedValue != null) {
                trie.put(prefix, aggregatedValue);
            }
        }
    }

    private <T> IPv4Address[] commonMergeIpsV4(@NotNull AssociativeAddressTrie<IPv4Address, T> trie) {
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

    private <T> IPv6Address[] commonMergeIpsV6(@NotNull AssociativeAddressTrie<IPv6Address, T> trie) {
        var firstAddedNode = trie.firstAddedNode();
        if (firstAddedNode == null) return new IPv6Address[0];
        var it = trie.nodeIterator(false);
        List<IPv6Address> ips = new ArrayList<>();
        while (it.hasNext()) {
            var node = it.next();
            ips.add(node.getKey().withoutPrefixLength().toPrefixBlock(60).toZeroHost());
        }
        IPv6Address[] array = new IPv6Address[ips.size()];
        return firstAddedNode.getKey().mergeToPrefixBlocks(ips.toArray(array));
    }

}
