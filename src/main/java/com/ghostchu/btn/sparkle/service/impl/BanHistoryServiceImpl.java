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
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final String DISTINCT_MODULE_NAMES_CACHE_KEY = "sparkle:banhistory:module_names:distinct_cache";
    @Autowired
    private ITorrentService torrentService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GeoIPManager geoIPManager;

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> stringStringRedisTemplate;


    @Transactional
    @Override
    public void syncBanHistory(@NotNull String submitterIp, long userAppId, @NotNull List<BtnBan> bans) {
        var nowTime = OffsetDateTime.now();
        var list = bans.stream().map(btnBan -> {
            Map<String, Object> structuredDataMap = Map.of();
            if (btnBan.getStructuredData() != null && !btnBan.getStructuredData().isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = objectMapper.readValue(btnBan.getStructuredData(), Map.class);
                    structuredDataMap = parsed;
                } catch (Exception e) {
                    // Log error or handle it appropriately
                    structuredDataMap = Map.of();
                }
            }
            var banAtTime = btnBan.getBanAt().toLocalDateTime().atOffset(ZoneOffset.UTC);
            // 相差不能超过 7 天
            if (banAtTime.isAfter(nowTime.plusHours(1)) || banAtTime.isBefore(nowTime.minusDays(7))) {
                log.debug("Ignoring ban entry with out-of-range banAt time: {}", btnBan.getBanAt());
                return null;
            }
            var inet = InetAddress.ofLiteral(btnBan.getPeerIp());
            return new BanHistory()
                    .setInsertTime(nowTime)
                    .setPopulateTime(banAtTime)
                    .setUserappsId(userAppId)
                    .setTorrentId(torrentService.getOrCreateTorrentId(btnBan.getTorrentIdentifier(), btnBan.getTorrentSize(), btnBan.isTorrentIsPrivate(), null, null))
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
                    .setPeerGeoip(geoIPManager.geoData(inet))
                    .setStructuredData(structuredDataMap);
        }).filter(Objects::nonNull).toList();
        if (list.isEmpty()) return;
        this.baseMapper.insert(list, 1000);
    }

    @Override
    public @NotNull IPage<BanHistory> fetchBanHistory(@NotNull OffsetDateTime afterTime, @Nullable String peerIp, @Nullable Long torrentId, @Nullable List<String> moduleNames, @NotNull Page<BanHistory> page) {
        QueryWrapper<BanHistory> wrapper = new QueryWrapper<BanHistory>()
                .eq(torrentId != null, "torrent_id", torrentId)
                .ge("insert_time", afterTime)
                .in(moduleNames != null && !moduleNames.isEmpty(), "module_name", moduleNames)
                .orderByDesc("insert_time");

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
    public @NotNull IPage<BanHistory> queryBanHistory(@NotNull BanHistoryQueryDto queryDto) {
        QueryWrapper<BanHistory> queryWrapper = new QueryWrapper<>();

        // Check if there are any search conditions
        boolean hasSearchConditions = hasSearchConditions(queryDto);

        // Time range filter
        queryWrapper.ge(queryDto.getInsertTimeStart() != null, "insert_time", queryDto.getInsertTimeStart())
                .le(queryDto.getInsertTimeEnd() != null, "insert_time", queryDto.getInsertTimeEnd())
                .eq(queryDto.getTorrentId() != null, "torrent_id", queryDto.getTorrentId())
                .eq(queryDto.getPeerPort() != null, "peer_port", queryDto.getPeerPort())
                .eq(queryDto.getPeerId() != null && !queryDto.getPeerId().isBlank(), "peer_id", queryDto.getPeerId())
                .eq(queryDto.getPeerClientName() != null && !queryDto.getPeerClientName().isBlank(), "peer_client_name", queryDto.getPeerClientName())
                .eq(queryDto.getModuleName() != null && !queryDto.getModuleName().isBlank(), "module_name", queryDto.getModuleName())
                .like(queryDto.getRule() != null && !queryDto.getRule().isBlank(), "rule", queryDto.getRule())
                .like(queryDto.getDescription() != null && !queryDto.getDescription().isBlank(), "description", queryDto.getDescription());


        // Sorting
        String sortBy = queryDto.getSortBy() != null ? queryDto.getSortBy() : "insert_time";
        String sortOrder = queryDto.getSortOrder() != null ? queryDto.getSortOrder().toLowerCase() : "desc";

        if ("asc".equals(sortOrder)) {
            queryWrapper.orderByAsc(sortBy);
        } else {
            queryWrapper.orderByDesc(sortBy);
        }

        // Pagination
        int page = (queryDto.getPage() != null && queryDto.getPage() > 0) ? queryDto.getPage() : 1;
        int size = (queryDto.getSize() != null && queryDto.getSize() > 0) ? queryDto.getSize() : 100;

        Page<BanHistory> pageRequest = new Page<>(page, size);

        // Disable count query if there are no search conditions to improve performance
        if (!hasSearchConditions) {
            pageRequest.setSearchCount(false);
        }


        if (queryDto.getPeerIp() != null && !queryDto.getPeerIp().isBlank()) {
            queryWrapper.apply("peer_ip <<= {0}::inet", queryDto.getPeerIp());
            pageRequest.setOptimizeCountSql(false); // workarond for c.b.m.e.p.i.PaginationInnerInterceptor   : optimize this sql to a count sql has exception, sql:"SELECT  id,userapps_id,user_downloader,torrent_id,peer_ip,peer_port,peer_id,peer_client_name,peer_progress,from_peer_traffic,to_peer_traffic,from_peer_traffic_offset,to_peer_traffic_offset,flags,first_time_seen,last_time_seen,user_progress  FROM swarm_tracker      WHERE  (last_time_seen >= ? AND peer_ip <<= ?::inet) ORDER BY last_time_seen DESC", exception java.util.concurrent.ExecutionException: net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "<<" "<<" at line 1, column 306. Was expecting one of: ")"
            pageRequest.setOptimizeJoinOfCountSql(false);
        }


        return this.baseMapper.selectPage(pageRequest, queryWrapper);
    }

    /**
     * Check if the query DTO has any search conditions (excluding pagination and sorting)
     */
    private boolean hasSearchConditions(@NotNull BanHistoryQueryDto queryDto) {
        return queryDto.getInsertTimeStart() != null
                || queryDto.getInsertTimeEnd() != null
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

    @Scheduled(cron = "${sparkle.banhistory.refresh-module-name-cache-cron}")
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
        }
    }

}
