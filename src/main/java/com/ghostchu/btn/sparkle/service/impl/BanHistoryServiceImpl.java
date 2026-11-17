package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.dto.BanHistoryQueryDto;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.BanHistoryMapper;
import com.ghostchu.btn.sparkle.service.BanSubmissionGuard;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.TorrentRegistration;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.service.points.PointAwardContext;
import com.ghostchu.btn.sparkle.service.points.PointRulesService;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import com.ghostchu.btn.sparkle.service.dto.UniversalCountDto;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.service.moderation.BanStructuredData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
@Slf4j
public class BanHistoryServiceImpl extends ServiceImpl<BanHistoryMapper, BanHistory> implements IBanHistoryService {
    @Autowired
    private com.ghostchu.btn.sparkle.service.CareerIngestionService careerIngestionService;
    private static final String DISTINCT_MODULE_NAMES_CACHE_KEY = "sparkle:banhistory:module_names:distinct_cache";
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "populate_time", "torrent_id", "peer_ip", "peer_port", "peer_id",
            "peer_client_name", "peer_progress", "module_name", "rule", "insert_time"
    );
    @Autowired
    private ITorrentService torrentService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IPDBManager geoIPManager;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> stringStringRedisTemplate;
    @Autowired
    private PointAwardContext pointAwardContext;
    @Autowired
    private PointRulesService pointRulesService;
    @Autowired
    private BanSubmissionGuard banSubmissionGuard;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Override
    public void syncBanHistory(
            @NotNull String submitterIp,
            long userAppId,
            long userId,
            @NotNull List<BtnBan> bans
    ) {
        var nowTime = OffsetDateTime.now(ZoneOffset.UTC);
        var oldest = nowTime.minusDays(7);
        var newest = nowTime.plusHours(1);
        var candidates = bans.stream().map(ban -> {
            if (ban.getBanAt() == null || ban.getModule() == null || ban.getTorrentIdentifier() == null) {
                return null;
            }
            var banAt = ban.getBanAt().toInstant().atOffset(ZoneOffset.UTC);
            if (banAt.isBefore(oldest) || banAt.isAfter(newest)
                    || ban.getModule().endsWith("IdleConnectionDosProtection")
                    || ban.getModule().endsWith("AntiVampire")) {
                return null;
            }
            return new BanSubmissionGuard.Candidate(ban, InetAddress.ofLiteral(ban.getPeerIp()), banAt);
        }).filter(Objects::nonNull).toList();
        var prepared = new java.util.HashMap<BanSubmissionGuard.Candidate, BanHistory>();
        for (var candidate : candidates) {
            var btnBan = candidate.ban();
            Map<String, Object> structuredDataMap = btnBan.getParsedStructuredData();
            if (structuredDataMap == null) {
                structuredDataMap = BanStructuredData.parse(objectMapper, btnBan.getStructuredData());
            }
            var inet = candidate.ip();
            TorrentRegistration registration = torrentService.getOrCreateTorrent(
                    btnBan.getTorrentIdentifier(),
                    btnBan.getTorrentSize(),
                    btnBan.isTorrentIsPrivate(),
                    null,
                    null
            );
            prepared.put(candidate, new BanHistory()
                    .setInsertTime(nowTime)
                    .setPopulateTime(candidate.banAt())
                    .setUserappsId(userAppId)
                    .setTorrentId(registration.id())
                    .setPeerIp(inet)
                    .setPeerPort(btnBan.getPeerPort())
                    .setPeerId(btnBan.getPeerId())
                    .setPeerClientName(btnBan.getPeerClientName())
                    .setPeerProgress(btnBan.getPeerProgress())
                    .setPeerFlags(btnBan.getPeerFlag())
                    .setReporterProgress(btnBan.getDownloaderProgress())
                    .setToPeerTraffic(btnBan.getToPeerTraffic())
                    .setFromPeerTraffic(btnBan.getFromPeerTraffic())
                    .setModuleName(btnBan.getModule())
                    .setRule(btnBan.getRule())
                    .setDescription(btnBan.getDescription())
                    .setPeerGeoip(geoIPManager.queryIPDB(inet).geoData().get())
                    .setStructuredData(structuredDataMap));
        }
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var list = banSubmissionGuard.filter(userAppId, candidates, nowTime).stream().map(prepared::get).toList();
            if (list.isEmpty()) return;
            this.baseMapper.insert(list, 1000);
            careerIngestionService.bans(userId,list);
            pointAwardContext.recordConfigured(
                    userId,
                    PointCategory.BAN_HISTORY,
                    list.size(),
                    0
            );
            long ruleHitCount = list.stream()
                    .filter(ban -> ban.getRule() != null && !ban.getRule().isBlank())
                    .count();
            if (ruleHitCount > 0) {
                pointAwardContext.recordConfigured(
                        userId,
                        PointCategory.RULE_HIT_IP,
                        ruleHitCount,
                        0
                );
            }

            long wastedUploadBytes = 0;
            for (BanHistory ban : list) {
                long traffic = ban.getToPeerTraffic();
                if (traffic > 0) {
                    wastedUploadBytes = wastedUploadBytes > Long.MAX_VALUE - traffic
                            ? Long.MAX_VALUE
                            : wastedUploadBytes + traffic;
                }
            }
            long wastedPoints = BigInteger.valueOf(wastedUploadBytes)
                    .multiply(BigInteger.valueOf(
                            pointRulesService.points(PointCategory.WASTED_UPLOAD)
                    ))
                    .divide(BigInteger.valueOf(1_048_576L)
                            .multiply(BigInteger.valueOf(
                                    pointRulesService.wastedUploadMib()
                            )))
                    .min(BigInteger.valueOf(Long.MAX_VALUE))
                    .longValue();
            if (wastedPoints > 0) {
                pointAwardContext.record(
                        userId,
                        PointCategory.WASTED_UPLOAD,
                        wastedPoints,
                        list.size(),
                        wastedUploadBytes
                );
            }
        });
    }

    @Override
    public @NotNull IPage<BanHistory> fetchBanHistory(@NotNull OffsetDateTime afterTime, @Nullable String peerIp, @Nullable Long torrentId, @Nullable List<String> moduleNames, @NotNull Page<BanHistory> page) {
        QueryWrapper<BanHistory> wrapper = new QueryWrapper<BanHistory>()
                .eq(torrentId != null, "torrent_id", torrentId)
                .ge("populate_time", afterTime)
                .in(moduleNames != null && !moduleNames.isEmpty(), "module_name", moduleNames)
                .orderByDesc("populate_time");

        // Peer IP filter - supports both single IP and CIDR notation using <<= operator
        if (peerIp != null && !peerIp.isBlank()) {
            wrapper.apply("peer_ip <<= {0}::inet", peerIp.trim());
            page.setOptimizeCountSql(false); // workarond for c.b.m.e.p.i.PaginationInnerInterceptor   : optimize this sql to a count sql has exception, sql:"SELECT  id,userapps_id,user_downloader,torrent_id,peer_ip,peer_port,peer_id,peer_client_name,peer_progress,from_peer_traffic,to_peer_traffic,from_peer_traffic_offset,to_peer_traffic_offset,flags,first_time_seen,last_time_seen,user_progress  FROM swarm_tracker      WHERE  (last_time_seen >= ? AND peer_ip <<= ?::inet) ORDER BY last_time_seen DESC", exception java.util.concurrent.ExecutionException: net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "<<" "<<" at line 1, column 306. Was expecting one of: ")"
            page.setOptimizeJoinOfCountSql(false);
        }

        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public @Nullable PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp) {
        return this.baseMapper.sumPeerIpTraffic(afterTimestamp, peerIp);
    }

    @Override
    public List<Long> selectPeerTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull String peerIp) {
        return this.baseMapper.selectPeerTorrents(afterTimestamp, peerIp);
    }

    @Override
    public @NotNull QueryResult queryBanHistory(@NotNull BanHistoryQueryDto queryDto) {
        TimeRange range = resolveTimeRange(queryDto, OffsetDateTime.now(ZoneOffset.UTC));
        QueryWrapper<BanHistory> queryWrapper = buildQuery(queryDto, range);
        // Pagination
        int page = (queryDto.getPage() != null && queryDto.getPage() > 0) ? queryDto.getPage() : 1;
        int size = (queryDto.getSize() != null && queryDto.getSize() > 0)
                ? Math.min(queryDto.getSize(), 100)
                : 100;

        Page<BanHistory> pageRequest = new Page<>(page, size);

        // Disable count query if there are no search conditions to improve performance
        if (!hasSearchConditions(queryDto)) {
            pageRequest.setSearchCount(false);
        }


        if (queryDto.getPeerIp() != null && !queryDto.getPeerIp().isBlank()) {
            pageRequest.setOptimizeCountSql(false); // workarond for c.b.m.e.p.i.PaginationInnerInterceptor   : optimize this sql to a count sql has exception, sql:"SELECT  id,userapps_id,user_downloader,torrent_id,peer_ip,peer_port,peer_id,peer_client_name,peer_progress,from_peer_traffic,to_peer_traffic,from_peer_traffic_offset,to_peer_traffic_offset,flags,first_time_seen,last_time_seen,user_progress  FROM swarm_tracker      WHERE  (last_time_seen >= ? AND peer_ip <<= ?::inet) ORDER BY last_time_seen DESC", exception java.util.concurrent.ExecutionException: net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "<<" "<<" at line 1, column 306. Was expecting one of: ")"
            pageRequest.setOptimizeJoinOfCountSql(false);
        }


        return new QueryResult(this.baseMapper.selectPage(pageRequest, queryWrapper), range);
    }

    public static QueryWrapper<BanHistory> buildQuery(BanHistoryQueryDto queryDto, OffsetDateTime now) {
        return buildQuery(queryDto, resolveTimeRange(queryDto, now));
    }

    public static TimeRange resolveTimeRange(BanHistoryQueryDto queryDto, OffsetDateTime now) {
        OffsetDateTime populateTimeStart = Objects.requireNonNullElse(queryDto.getPopulateTimeStart(), now.minusDays(7));
        OffsetDateTime populateTimeEnd = Objects.requireNonNullElse(queryDto.getPopulateTimeEnd(), now);

        if (populateTimeStart.isAfter(populateTimeEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ban_history_range_reversed");
        }

        if (populateTimeEnd.isAfter(populateTimeStart.withOffsetSameInstant(ZoneOffset.UTC).plusMonths(3))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ban_history_range_too_long");
        }
        return new TimeRange(populateTimeStart, populateTimeEnd);
    }

    private static QueryWrapper<BanHistory> buildQuery(BanHistoryQueryDto queryDto, TimeRange range) {
        QueryWrapper<BanHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("populate_time", range.populateTimeStart())
                .le("populate_time", range.populateTimeEnd())
                .eq(queryDto.getTorrentId() != null, "torrent_id", queryDto.getTorrentId())
                .eq(queryDto.getPeerPort() != null, "peer_port", queryDto.getPeerPort())
                .likeRight(queryDto.getPeerId() != null && !queryDto.getPeerId().isBlank(), "peer_id", queryDto.getPeerId())
                .likeRight(queryDto.getPeerClientName() != null && !queryDto.getPeerClientName().isBlank(), "peer_client_name", queryDto.getPeerClientName())
                .eq(queryDto.getModuleName() != null && !queryDto.getModuleName().isBlank(), "module_name", queryDto.getModuleName())
                .likeRight(queryDto.getRule() != null && !queryDto.getRule().isBlank(), "rule", queryDto.getRule())
                .likeRight(queryDto.getDescription() != null && !queryDto.getDescription().isBlank(), "description", queryDto.getDescription());


        // Sorting
        String requestedSort = queryDto.getSortBy();
        String sortBy = requestedSort != null && SORTABLE_FIELDS.contains(requestedSort)
                ? requestedSort
                : "populate_time";
        String sortOrder = queryDto.getSortOrder() != null ? queryDto.getSortOrder().toLowerCase() : "desc";

        if ("asc".equals(sortOrder)) {
            queryWrapper.orderByAsc(sortBy);
        } else {
            queryWrapper.orderByDesc(sortBy);
        }

        if (queryDto.getPeerIp() != null && !queryDto.getPeerIp().isBlank()) {
            queryWrapper.apply("peer_ip <<= {0}::inet", queryDto.getPeerIp());
        }
        return queryWrapper;
    }

    /**
     * Check if the query DTO has any search conditions (excluding pagination and sorting)
     */
    private boolean hasSearchConditions(@NotNull BanHistoryQueryDto queryDto) {
        return queryDto.getPopulateTimeStart() != null
                || queryDto.getPopulateTimeEnd() != null
                || queryDto.getTorrentId() != null
                || (queryDto.getPeerIp() != null && !queryDto.getPeerIp().isBlank())
                || queryDto.getPeerPort() != null
                || (queryDto.getPeerId() != null && !queryDto.getPeerId().isBlank())
                || (queryDto.getPeerClientName() != null && !queryDto.getPeerClientName().isBlank())
                || (queryDto.getModuleName() != null && !queryDto.getModuleName().isBlank())
                || (queryDto.getRule() != null && !queryDto.getRule().isBlank())
                || (queryDto.getDescription() != null && !queryDto.getDescription().isBlank());
    }


    @SneakyThrows
    @Override
    public @NotNull List<String> getDistinctModuleNames() {
        String cache = stringStringRedisTemplate.opsForValue().get(DISTINCT_MODULE_NAMES_CACHE_KEY);
        if (cache == null) {
            refreshDistinctModuleNamesCache();
            cache = stringStringRedisTemplate.opsForValue().get(DISTINCT_MODULE_NAMES_CACHE_KEY);
        }
        return objectMapper.readValue(cache, new TypeReference<>() {
        });
    }

    @MonitoredScheduledTask(
            id = "ban-module-name-cache",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.ban-module-name-cache.name",
            descriptionKey = "admin.tasks.items.ban-module-name-cache.description",
            expression = "${sparkle.banhistory.refresh-module-name-cache-cron}"
    )
    public void refreshDistinctModuleNamesCache() {
        List<String> moduleNames = this.baseMapper.selectObjs(
                new QueryWrapper<BanHistory>()
                        .select("DISTINCT module_name")
                        .isNotNull("module_name")
                        .orderByAsc("module_name")
        ).stream().map(Object::toString).toList();
        try {
            String serialized = objectMapper.writeValueAsString(moduleNames);
            stringStringRedisTemplate.opsForValue().set(DISTINCT_MODULE_NAMES_CACHE_KEY, serialized);
        } catch (Exception e) {
            log.error("Failed to serialize distinct module names for caching", e);
            throw new IllegalStateException(
                    "Failed to cache distinct ban module names",
                    e
            );
        }
    }

    @Override
    public long estimateCountAll() {
        return this.baseMapper.estimateCountAll();
    }

    @Override
    public @NotNull UniversalCountDto countTimeStatistics() {
        return this.baseMapper.countTimeStatistics();
    }
}
