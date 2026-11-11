package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.entity.GeoIpCityBlacklistEntry;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.GeoIpCityBlacklistMapper;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoNetworkRecord;
import com.ghostchu.btn.sparkle.util.ipdb.IPDB;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class GeoIpCityBlacklistService
        extends ServiceImpl<GeoIpCityBlacklistMapper, GeoIpCityBlacklistEntry>
        implements IPDenyListRuleProvider {
    public static final String MODULE_ID = "geoip-city-blacklist";
    private static final String ENTRY_CONTENT_KEY_PREFIX =
            "sparkle:analyse:geoip_city_blacklist:entry:";
    private static final List<String> LOCATION_SUFFIXES = List.of(
            "特别行政区",
            "维吾尔自治区",
            "壮族自治区",
            "回族自治区",
            "自治区",
            "省",
            "市"
    );

    private final IPDBManager ipdbManager;
    private final AnalyseModuleConfigService moduleConfigService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean rerunRequested = new AtomicBoolean();
    private volatile GenerationStatus generationStatus = GenerationStatus.idle();

    public GeoIpCityBlacklistService(
            IPDBManager ipdbManager,
            AnalyseModuleConfigService moduleConfigService,
            @Qualifier("stringStringRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.ipdbManager = ipdbManager;
        this.moduleConfigService = moduleConfigService;
        this.redisTemplate = redisTemplate;
    }

    public List<GeoIpCityBlacklistEntry> listEntries() {
        return list(new LambdaQueryWrapper<GeoIpCityBlacklistEntry>()
                .orderByAsc(GeoIpCityBlacklistEntry::getId));
    }

    public GeoIpCityBlacklistEntry createEntry(
            String countryCode,
            String province,
            String city,
            boolean enabled,
            @Nullable Long operatorId
    ) throws BusinessException {
        Location location = normalizeLocation(countryCode, province, city);
        ensureUnique(location, null);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        GeoIpCityBlacklistEntry entry = new GeoIpCityBlacklistEntry()
                .setCountryCode(location.countryCode())
                .setProvince(location.province())
                .setCity(location.city())
                .setEnabled(enabled)
                .setIpv4CidrCount(0)
                .setIpv6CidrCount(0)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setCreatedBy(operatorId)
                .setUpdatedBy(operatorId);
        save(entry);
        requestRefresh(operatorId);
        return entry;
    }

    public GeoIpCityBlacklistEntry updateEntry(
            long id,
            String countryCode,
            String province,
            String city,
            boolean enabled,
            @Nullable Long operatorId
    ) throws BusinessException {
        GeoIpCityBlacklistEntry entry = requireEntry(id);
        Location location = normalizeLocation(countryCode, province, city);
        ensureUnique(location, id);
        entry.setCountryCode(location.countryCode())
                .setProvince(location.province())
                .setCity(location.city())
                .setEnabled(enabled)
                .setIpv4CidrCount(0)
                .setIpv6CidrCount(0)
                .setLastGeneratedAt(null)
                .setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .setUpdatedBy(operatorId);
        updateById(entry);
        update(new LambdaUpdateWrapper<GeoIpCityBlacklistEntry>()
                .eq(GeoIpCityBlacklistEntry::getId, id)
                .set(GeoIpCityBlacklistEntry::getLastGeneratedAt, null));
        if (!enabled) {
            redisTemplate.delete(entryContentKey(id));
        }
        requestRefresh(operatorId);
        return entry;
    }

    public void deleteEntry(long id, @Nullable Long operatorId) throws BusinessException {
        requireEntry(id);
        removeById(id);
        redisTemplate.delete(entryContentKey(id));
        requestRefresh(operatorId);
    }

    public String getGeneratedEntryContent(long id) throws BusinessException {
        requireEntry(id);
        String content = redisTemplate.opsForValue().get(entryContentKey(id));
        return content == null ? "" : content;
    }

    public @NotNull GenerationStatus status() {
        return generationStatus;
    }

    public @NotNull GenerationStatus requestRefresh(@Nullable Long operatorId) {
        if (!running.compareAndSet(false, true)) {
            rerunRequested.set(true);
            return generationStatus;
        }
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        generationStatus = GenerationStatus.started(startedAt, "MANUAL", operatorId);
        Thread.startVirtualThread(() -> executeGeneration(
                startedAt,
                System.nanoTime(),
                "MANUAL",
                operatorId
        ));
        return generationStatus;
    }

    public void generateScheduled() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        generationStatus = GenerationStatus.started(startedAt, "SCHEDULED", null);
        executeGeneration(startedAt, System.nanoTime(), "SCHEDULED", null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeGeneratedContent() {
        try {
            if (!moduleConfigService.isEnabled(MODULE_ID)) {
                clearCombinedOutput();
                return;
            }
            if (Boolean.FALSE.equals(redisTemplate.hasKey(
                    RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VALUE.getKey()
            )) && count(new LambdaQueryWrapper<GeoIpCityBlacklistEntry>()
                    .eq(GeoIpCityBlacklistEntry::getEnabled, true)) > 0) {
                requestRefresh(null);
            }
        } catch (Exception exception) {
            log.warn("Unable to initialize GeoIP city blacklist output: {}",
                    exception.getMessage());
        }
    }

    private void executeGeneration(
            OffsetDateTime startedAt,
            long startedNanos,
            String trigger,
            @Nullable Long operatorId
    ) {
        try {
            if (!moduleConfigService.isEnabled(MODULE_ID)) {
                clearCombinedOutput();
                finishGeneration(startedAt, startedNanos, trigger, operatorId, 0, 0, 0, null);
                return;
            }
            List<GeoIpCityBlacklistEntry> enabledEntries = list(
                    new LambdaQueryWrapper<GeoIpCityBlacklistEntry>()
                            .eq(GeoIpCityBlacklistEntry::getEnabled, true)
                            .orderByAsc(GeoIpCityBlacklistEntry::getId)
            );
            if (enabledEntries.isEmpty()) {
                clearCombinedOutput();
                finishGeneration(startedAt, startedNanos, trigger, operatorId, 0, 0, 0, null);
                return;
            }

            IPDB ipdb = ipdbManager.getIpdb();
            if (ipdb == null) {
                throw new IllegalStateException("GeoIP database is not available");
            }

            Map<Long, List<IPAddress>> rawIpv4 = new LinkedHashMap<>();
            Map<Long, List<IPAddress>> rawIpv6 = new LinkedHashMap<>();
            enabledEntries.forEach(entry -> {
                rawIpv4.put(entry.getId(), new ArrayList<>());
                rawIpv6.put(entry.getId(), new ArrayList<>());
            });

            if (enabledEntries.stream().anyMatch(this::usesChineseGeoDatabase)) {
                ipdb.forEachChineseCityNetwork(record ->
                        collectMatches(record, enabledEntries, rawIpv4, rawIpv6)
                );
            }
            if (enabledEntries.stream().anyMatch(entry -> !usesChineseGeoDatabase(entry))) {
                ipdb.forEachWorldCityNetwork(record ->
                        collectMatches(record, enabledEntries, rawIpv4, rawIpv6)
                );
            }

            Collection<IPAddress> combinedIpv4 = new ArrayList<>();
            Collection<IPAddress> combinedIpv6 = new ArrayList<>();
            StringBuilder combinedContent = new StringBuilder();
            OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            int generatedEntries = 0;
            for (GeoIpCityBlacklistEntry entry : enabledEntries) {
                List<IPAddress> ipv4 = mergeNetworks(rawIpv4.getOrDefault(entry.getId(), List.of()));
                List<IPAddress> ipv6 = mergeNetworks(rawIpv6.getOrDefault(entry.getId(), List.of()));
                String content = renderContent(entry, ipv4, ipv6);
                redisTemplate.opsForValue().set(entryContentKey(entry.getId()), content);
                if (!combinedContent.isEmpty()) {
                    combinedContent.append("\n\n");
                }
                combinedContent.append(content);
                update(new LambdaUpdateWrapper<GeoIpCityBlacklistEntry>()
                        .eq(GeoIpCityBlacklistEntry::getId, entry.getId())
                        .set(GeoIpCityBlacklistEntry::getIpv4CidrCount, ipv4.size())
                        .set(GeoIpCityBlacklistEntry::getIpv6CidrCount, ipv6.size())
                        .set(GeoIpCityBlacklistEntry::getLastGeneratedAt, generatedAt));
                combinedIpv4.addAll(ipv4);
                combinedIpv6.addAll(ipv6);
                if (!ipv4.isEmpty() || !ipv6.isEmpty()) {
                    generatedEntries++;
                }
            }

            List<IPAddress> mergedIpv4 = mergeNetworks(combinedIpv4);
            List<IPAddress> mergedIpv6 = mergeNetworks(combinedIpv6);
            if (!moduleConfigService.isEnabled(MODULE_ID)) {
                clearCombinedOutput();
                finishGeneration(
                        startedAt,
                        startedNanos,
                        trigger,
                        operatorId,
                        enabledEntries.size(),
                        mergedIpv4.size(),
                        mergedIpv6.size(),
                        null
                );
                return;
            }
            writeCombinedOutput(combinedContent.toString());
            finishGeneration(
                    startedAt,
                    startedNanos,
                    trigger,
                    operatorId,
                    enabledEntries.size(),
                    mergedIpv4.size(),
                    mergedIpv6.size(),
                    null
            );
            log.info(
                    "Generated GeoIP city blacklist: {} entries, IPv4 CIDRs={}, IPv6 CIDRs={}, matched entries={}",
                    enabledEntries.size(),
                    mergedIpv4.size(),
                    mergedIpv6.size(),
                    generatedEntries
            );
        } catch (Exception exception) {
            log.error("Unable to generate GeoIP city blacklist", exception);
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            finishGeneration(startedAt, startedNanos, trigger, operatorId, 0, 0, 0, message);
        } finally {
            running.set(false);
            if (rerunRequested.getAndSet(false)) {
                Thread.startVirtualThread(() -> requestRefresh(operatorId));
            }
        }
    }

    private void collectMatches(
            GeoNetworkRecord record,
            List<GeoIpCityBlacklistEntry> entries,
            Map<Long, List<IPAddress>> ipv4,
            Map<Long, List<IPAddress>> ipv6
    ) {
        if (record.countryIso() == null || record.network() == null) {
            return;
        }
        IPAddress network = IPAddressUtil.getIPAddress(record.network().toString());
        if (network == null) {
            return;
        }
        for (GeoIpCityBlacklistEntry entry : entries) {
            if (!matches(entry, record)) {
                continue;
            }
            boolean isIpv4 = record.network().getNetworkAddress() instanceof Inet4Address;
            (isIpv4 ? ipv4 : ipv6)
                    .get(entry.getId())
                    .add(network);
        }
    }

    boolean matches(GeoIpCityBlacklistEntry entry, GeoNetworkRecord record) {
        if (!entry.getCountryCode().equalsIgnoreCase(record.countryIso())) {
            return false;
        }
        return matchesName(entry.getProvince(), record.provinceNames())
                && matchesName(entry.getCity(), record.cityNames());
    }

    private boolean matchesName(String expected, Set<String> candidates) {
        String normalizedExpected = normalizeName(expected);
        if (normalizedExpected.isEmpty()) {
            return false;
        }
        return candidates.stream()
                .map(this::normalizeName)
                .anyMatch(normalizedExpected::equals);
    }

    String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        for (String suffix : LOCATION_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
    }

    List<IPAddress> mergeNetworks(Collection<IPAddress> networks) {
        LinkedHashSet<IPAddress> unique = new LinkedHashSet<>(networks);
        List<IPAddress> merged = new ArrayList<>();
        mergeFamily(unique.stream().filter(IPAddress::isIPv4).toList(), merged);
        mergeFamily(unique.stream().filter(IPAddress::isIPv6).toList(), merged);
        merged.sort(Comparator
                .comparing(IPAddress::isIPv6)
                .thenComparing(IPAddress::compareTo));
        return List.copyOf(merged);
    }

    private void mergeFamily(List<IPAddress> networks, List<IPAddress> output) {
        if (networks.isEmpty()) {
            return;
        }
        IPAddress[] blocks = networks.toArray(IPAddress[]::new);
        output.addAll(List.of(networks.getFirst().mergeToPrefixBlocks(blocks)));
    }

    String renderContent(
            GeoIpCityBlacklistEntry entry,
            List<IPAddress> ipv4,
            List<IPAddress> ipv6
    ) {
        StringBuilder content = new StringBuilder();
        String description = rangeDescription(entry);
        content.append("#---START--- ").append(description).append('\n');
        ipv4.forEach(network -> content.append(network.toCompressedString()).append('\n'));
        ipv6.forEach(network -> content.append(network.toCompressedString()).append('\n'));
        content.append("#---END--- ").append(description);
        return content.toString().stripTrailing();
    }

    private String rangeDescription(GeoIpCityBlacklistEntry entry) {
        return "Sparkle3 GeoIP 城市黑名单 | "
                + entry.getCountryCode()
                + " "
                + entry.getProvince()
                + " / "
                + entry.getCity();
    }

    private void writeCombinedOutput(String content) {
        String version = Hashing.crc32c()
                .hashString(content, StandardCharsets.UTF_8)
                .toString();
        redisTemplate.opsForValue().set(
                RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VALUE.getKey(),
                content
        );
        redisTemplate.opsForValue().set(
                RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VERSION.getKey(),
                version
        );
    }

    private void clearCombinedOutput() {
        redisTemplate.delete(List.of(
                RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VALUE.getKey(),
                RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VERSION.getKey()
        ));
    }

    private void finishGeneration(
            OffsetDateTime startedAt,
            long startedNanos,
            String trigger,
            @Nullable Long operatorId,
            int entries,
            int ipv4Cidrs,
            int ipv6Cidrs,
            @Nullable String error
    ) {
        generationStatus = new GenerationStatus(
                false,
                startedAt,
                OffsetDateTime.now(ZoneOffset.UTC),
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(),
                entries,
                ipv4Cidrs,
                ipv6Cidrs,
                trigger,
                operatorId,
                error
        );
    }

    private Location normalizeLocation(String countryCode, String province, String city)
            throws BusinessException {
        String normalizedCountry = countryCode == null || countryCode.isBlank()
                ? "CN"
                : countryCode.trim().toUpperCase(Locale.ROOT);
        String normalizedProvince = province == null ? "" : province.trim();
        String normalizedCity = city == null ? "" : city.trim();
        if (!normalizedCountry.matches("[A-Z]{2}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Country code must contain two letters");
        }
        if (normalizedProvince.isEmpty() || normalizedProvince.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Province is required and must not exceed 100 characters");
        }
        if (normalizedCity.isEmpty() || normalizedCity.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "City is required and must not exceed 100 characters");
        }
        return new Location(normalizedCountry, normalizedProvince, normalizedCity);
    }

    private void ensureUnique(Location location, @Nullable Long excludedId)
            throws BusinessException {
        LambdaQueryWrapper<GeoIpCityBlacklistEntry> query =
                new LambdaQueryWrapper<GeoIpCityBlacklistEntry>()
                        .eq(GeoIpCityBlacklistEntry::getCountryCode, location.countryCode())
                        .eq(GeoIpCityBlacklistEntry::getProvince, location.province())
                        .eq(GeoIpCityBlacklistEntry::getCity, location.city())
                        .ne(excludedId != null, GeoIpCityBlacklistEntry::getId, excludedId);
        if (count(query) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This GeoIP location already exists");
        }
    }

    private GeoIpCityBlacklistEntry requireEntry(long id) throws BusinessException {
        GeoIpCityBlacklistEntry entry = getById(id);
        if (entry == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "GeoIP city blacklist entry not found");
        }
        return entry;
    }

    private boolean usesChineseGeoDatabase(GeoIpCityBlacklistEntry entry) {
        return "CN".equalsIgnoreCase(entry.getCountryCode());
    }

    private String entryContentKey(long id) {
        return ENTRY_CONTENT_KEY_PREFIX + id;
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue()
                .get(RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue()
                .get(RedisKeyConstant.ANALYSE_GEOIP_CITY_BLACKLIST_VALUE.getKey());
    }

    public record GenerationStatus(
            boolean running,
            OffsetDateTime lastStartedAt,
            OffsetDateTime lastFinishedAt,
            long lastDurationMs,
            int enabledEntries,
            int ipv4CidrCount,
            int ipv6CidrCount,
            String trigger,
            Long triggeredBy,
            String lastError
    ) {
        private static GenerationStatus idle() {
            return new GenerationStatus(
                    false,
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    null
            );
        }

        private static GenerationStatus started(
                OffsetDateTime startedAt,
                String trigger,
                @Nullable Long operatorId
        ) {
            return new GenerationStatus(
                    true,
                    startedAt,
                    null,
                    0,
                    0,
                    0,
                    0,
                    trigger,
                    operatorId,
                    null
            );
        }
    }

    private record Location(String countryCode, String province, String city) {
    }
}
