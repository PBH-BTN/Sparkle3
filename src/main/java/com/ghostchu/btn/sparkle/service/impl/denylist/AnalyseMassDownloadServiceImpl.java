package com.ghostchu.btn.sparkle.service.impl.denylist;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseMassDownloadResult;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.service.impl.AbstractAnalyseRuleServiceImpl;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AnalyseMassDownloadServiceImpl
        extends AbstractAnalyseRuleServiceImpl
        implements IPDenyListRuleProvider {
    public static final String MODULE_ID = "mass-download-monitor";

    @Value("${sparkle.analyse.mass-download-monitor.duration}")
    private long duration;
    @Value("${sparkle.analyse.mass-download-monitor.ipv4-prefix}")
    private int ipv4Prefix;
    @Value("${sparkle.analyse.mass-download-monitor.ipv6-prefix}")
    private int ipv6Prefix;
    @Value("${sparkle.analyse.mass-download-monitor.threshold-download}")
    private long thresholdDownload;

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private AnalyseModuleConfigService moduleConfigService;

    @Transactional(readOnly = true)
    public void analyseMassDownload() {
        if (!moduleConfigService.isEnabled(MODULE_ID)) {
            return;
        }
        long activeDuration = moduleConfigService.durationMillis(
                MODULE_ID, "duration", duration
        );
        int activeIpv4Prefix = moduleConfigService.integerValue(
                MODULE_ID, "ipv4-prefix", ipv4Prefix
        );
        int activeIpv6Prefix = moduleConfigService.integerValue(
                MODULE_ID, "ipv6-prefix", ipv6Prefix
        );
        long activeThreshold = moduleConfigService.longValue(
                MODULE_ID, "threshold-download", thresholdDownload
        );
        if (!validConfiguration(activeDuration, activeIpv4Prefix, activeIpv6Prefix, activeThreshold)) {
            log.error(
                    "Invalid mass download monitor configuration: duration={}, ipv4Prefix={}, "
                            + "ipv6Prefix={}, thresholdDownload={}",
                    activeDuration,
                    activeIpv4Prefix,
                    activeIpv6Prefix,
                    activeThreshold
            );
            return;
        }

        OffsetDateTime afterTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
                .minus(activeDuration, ChronoUnit.MILLIS);
        Map<IPAddress, GeneratedRule> rules = new LinkedHashMap<>();
        try (var cursor = baseMapper.analyseMassDownload(
                afterTimestamp,
                activeIpv4Prefix,
                activeIpv6Prefix,
                BigInteger.valueOf(activeThreshold)
        )) {
            for (AnalyseMassDownloadResult result : cursor) {
                IPAddress address = IPAddressUtil.getIPAddress(result.getPeerIpCidr());
                if (address == null
                        || address.isZero()
                        || (address.getPrefixLength() != null && address.getPrefixLength() == 0)) {
                    continue;
                }
                GeneratedRule generated = new GeneratedRule(
                        address,
                        parse(result.getDownloadBytes()),
                        value(result.getReporterCount()),
                        value(result.getConnectionCount()),
                        value(result.getTorrentCount()),
                        result.getLatestSeenAt()
                );
                rules.merge(address, generated, GeneratedRule::plus);
            }
        } catch (Exception exception) {
            log.error("Unable to analyze mass download records", exception);
            return;
        }

        List<GeneratedRule> sorted = new ArrayList<>(rules.values());
        sorted.sort(Comparator
                .comparing((GeneratedRule rule) -> rule.getAddress().isIPv6())
                .thenComparing(rule -> rule.getAddress()));

        StringBuilder content = new StringBuilder();
        for (GeneratedRule rule : sorted) {
            content.append("# [Sparkle3 大量下载监测] 统计窗口: ")
                    .append(formatDuration(activeDuration))
                    .append(", 报告者: ").append(rule.getReporterCount())
                    .append(", 连接记录: ").append(rule.getConnectionCount())
                    .append(", 种子: ").append(rule.getTorrentCount())
                    .append(", 对方从报告者下载(to_peer_traffic): ")
                    .append(formatBytes(rule.getDownloadBytes()))
                    .append(", 最近观测: ")
                    .append(rule.getLatestSeenAt() == null
                            ? "N/A"
                            : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(rule.getLatestSeenAt()))
                    .append('\n')
                    .append(formatRuleAddress(rule.getAddress()))
                    .append('\n');
        }

        String value = content.toString();
        String version = Hashing.crc32c()
                .hashString(value, StandardCharsets.UTF_8)
                .toString();
        redisTemplate.opsForValue().set(
                RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VALUE.getKey(),
                value
        );
        redisTemplate.opsForValue().set(
                RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VERSION.getKey(),
                version
        );
        log.info(
                "Mass download monitor completed: window={}ms, ipv4Prefix={}, ipv6Prefix={}, "
                        + "threshold={} bytes, rules={}, version={}",
                activeDuration,
                activeIpv4Prefix,
                activeIpv6Prefix,
                activeThreshold,
                sorted.size(),
                version
        );
    }

    private boolean validConfiguration(
            long activeDuration,
            int activeIpv4Prefix,
            int activeIpv6Prefix,
            long activeThreshold
    ) {
        return activeDuration >= 3_600_000L
                && activeDuration <= 604_800_000L
                && activeIpv4Prefix >= 8
                && activeIpv4Prefix <= 32
                && activeIpv6Prefix >= 16
                && activeIpv6Prefix <= 128
                && activeThreshold > 0;
    }

    private BigInteger parse(String value) {
        try {
            return value == null ? BigInteger.ZERO : new BigInteger(value);
        } catch (NumberFormatException exception) {
            return BigInteger.ZERO;
        }
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    String formatRuleAddress(IPAddress address) {
        if (address.isIPv4() && Integer.valueOf(32).equals(address.getPrefixLength())) {
            return address.withoutPrefixLength().toCompressedString();
        }
        return address.toCompressedString();
    }

    private String formatDuration(long durationMillis) {
        long minutes = durationMillis / 60_000L;
        if (minutes % 1_440 == 0) {
            return (minutes / 1_440) + "天";
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + "小时";
        }
        return minutes + "分钟";
    }

    private String formatBytes(BigInteger bytes) {
        String[] units = {"Bytes", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        BigDecimal value = new BigDecimal(bytes);
        int index = 0;
        BigDecimal divisor = BigDecimal.valueOf(1024);
        while (index < units.length - 1 && value.compareTo(divisor) >= 0) {
            value = value.divide(divisor, MathContext.DECIMAL64);
            index++;
        }
        return value.stripTrailingZeros().toPlainString() + " " + units[index];
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        String value = redisTemplate.opsForValue()
                .get(RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VALUE.getKey());
        String version = redisTemplate.opsForValue()
                .get(RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue()
                .get(RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue()
                .get(RedisKeyConstant.ANALYSE_MASS_DOWNLOAD_VALUE.getKey());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedRule {
        private IPAddress address;
        private BigInteger downloadBytes;
        private long reporterCount;
        private long connectionCount;
        private long torrentCount;
        private OffsetDateTime latestSeenAt;

        private GeneratedRule plus(GeneratedRule other) {
            return new GeneratedRule(
                    address,
                    downloadBytes.add(other.downloadBytes),
                    reporterCount + other.reporterCount,
                    connectionCount + other.connectionCount,
                    torrentCount + other.torrentCount,
                    latestSeenAt == null
                            ? other.latestSeenAt
                            : other.latestSeenAt == null || latestSeenAt.isAfter(other.latestSeenAt)
                            ? latestSeenAt
                            : other.latestSeenAt
            );
        }
    }
}
