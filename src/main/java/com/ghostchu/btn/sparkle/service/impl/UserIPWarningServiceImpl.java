package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class UserIPWarningServiceImpl {
    @Value("${sparkle.admin.user-ip-warning.duration}")
    private long duration;
    @Autowired
    private List<IPDenyListRuleProvider> ipDenyListRuleProviders;
    @Autowired
    private IUserappsHeartbeatService heartbeatService;
    @Qualifier("stringObjectRedisTemplate")
    @Autowired
    private RedisTemplate<String, Object> stringObjectRedisTemplate;

    @Scheduled(cron = "${sparkle.admin.user-ip-warning.cron}")
    public void userIpWarning() {
        Map<IPAddress, String> map = new LinkedHashMap<>();
        DualIPv4v6AssociativeTries<String> ips = new DualIPv4v6AssociativeTries<>();
        ipDenyListRuleProviders.forEach(provider -> stringToIPList(provider.getContent(), ips));
        ips.nodeIterator(false).forEachRemaining(node -> {
            var records = heartbeatService.fetchIpHeartbeatRecords(node.getKey().toNormalizedString(),
                    OffsetDateTime.now().minus(duration, ChronoUnit.MILLIS));
            if (!records.isEmpty()) {
                map.put(node.getKey(), "AppID: " + records.getFirst().getUserappId());
            }
        });
        stringObjectRedisTemplate.opsForValue().set(RedisKeyConstant.ADMIN_USER_IP_HEARTBEAT_WARNING_LIST.getKey(), map);
    }

    /**
     * 读取规则文本并转为IpList
     *
     * @param data 规则文本
     * @param ips  ip列表
     * @return 加载的行数
     */
    private int stringToIPList(String data, DualIPv4v6AssociativeTries<String> ips) {
        AtomicInteger count = new AtomicInteger();
        StringJoiner sj = new StringJoiner("\n");
        for (String ele : data.split("\n")) {
            if (ele.isBlank()) continue;
            if (ele.startsWith("#")) {
                // add into sj but without hashtag prefix
                sj.add(ele.substring(1));
                continue;
            }
            try {
                var parsedIp = parseRuleLine(ele, sj.toString());
                if (parsedIp != null) {
                    count.getAndIncrement();
                    ips.put(parsedIp.getKey(), parsedIp.getValue());
                }
            } catch (Exception e) {
                log.error("Unable parse rule: {}", ele, e);
            } finally {
                sj = new StringJoiner("\n");
            }
        }
        return count.get();
    }

    private Map.Entry<IPAddress, @Nullable String> parseRuleLine(String ele, String preReadComment) {
        // 检查是否是 DAT/eMule 格式
        // 016.000.000.000 , 016.255.255.255 , 200 , Yet another organization
        // 032.000.000.000 , 032.255.255.255 , 200 , And another
        if (ele.contains(",")) {
            var spilted = ele.split(",");
            if (spilted.length < 3) {
                return null;
            }
            IPAddress start = IPAddressUtil.getIPAddress(spilted[0]);
            IPAddress end = IPAddressUtil.getIPAddress(spilted[1]);
            int level = Integer.parseInt(spilted[2]);
            String comment = spilted.length > 3 ? spilted[3] : preReadComment;
            if (level >= 128) return null;
            if (start == null || end == null) return null;
            return Map.entry(start.spanWithRange(end).coverWithPrefixBlock(), comment);
        } else {
            // ip #end-line-comment
            String ip;
            if (ele.contains("#")) {
                ip = ele.substring(0, ele.indexOf("#"));
                String comment = null;
                if (ele.contains("#")) {
                    comment = ele.substring(ele.indexOf("#") + 1);
                }
                return Map.entry(IPAddressUtil.getIPAddress(ip), Optional.ofNullable(comment).orElse(preReadComment));
            } else {
                return Map.entry(IPAddressUtil.getIPAddress(ele), preReadComment);
            }
        }
    }
}
