package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.dto.BanHistoryQueryDto;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.BanHistoryMapper;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
public class BanHistoryServiceImpl extends ServiceImpl<BanHistoryMapper, BanHistory> implements IBanHistoryService {
    @Autowired
    private ITorrentService torrentService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GeoIPManager geoIPManager;


    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void syncBanHistory(@NotNull String submitterIp, long userAppId, @NotNull List<BtnBan> bans) {
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
            var inet = InetAddress.ofLiteral(btnBan.getPeerIp());
            return new BanHistory()
                    .setInsertTime(LocalDateTime.now().atOffset(ZoneOffset.UTC))
                    .setPopulateTime(btnBan.getBanAt().toLocalDateTime().atOffset(ZoneOffset.UTC))
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
        }).toList();
        if (list.isEmpty()) return;
        this.baseMapper.insert(list, 1000);
    }

    @Override
    public @NotNull IPage<BanHistory> fetchBanHistory(@NotNull OffsetDateTime afterTime, @Nullable InetAddress peerIp, @Nullable Long torrentId, @Nullable List<String> moduleNames, @NotNull Page<BanHistory> page) {
        return this.baseMapper.selectPage(
                page,
                new QueryWrapper<BanHistory>()
                        .eq(torrentId != null, "torrent_id", torrentId)
                        .eq(peerIp != null, "peer_ip", peerIp)
                        .ge("insert_time", afterTime)
                        .in(moduleNames != null && !moduleNames.isEmpty(), "module_name", moduleNames)
                        .orderByDesc("insert_time")
        );
    }

    @Override
    public @Nullable PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp) {
        return this.baseMapper.sumPeerIpTraffic(afterTimestamp, peerIp);
    }

    @Override
    public List<Long> selectPeerTorrents(@NotNull OffsetDateTime afterTimestamp, @NotNull InetAddress peerIp) {
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
        // Peer IP filter
        if (queryDto.getPeerIp() != null && !queryDto.getPeerIp().isBlank()) {
            try {
                InetAddress peerIp = InetAddress.ofLiteral(queryDto.getPeerIp().trim());
                queryWrapper.eq("peer_ip", peerIp);
            } catch (Exception e) {
                // Invalid IP, ignore filter
            }
        }

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

    @Override
    @Cacheable(value = "banHistoryDistinctModuleNames#1800000")
    public @NotNull List<String> getDistinctModuleNames() {
        return this.baseMapper.selectObjs(
                new QueryWrapper<BanHistory>()
                        .select("DISTINCT module_name")
                        .isNotNull("module_name")
                        .orderByAsc("module_name")
        ).stream().map(Object::toString).toList();
    }

}
