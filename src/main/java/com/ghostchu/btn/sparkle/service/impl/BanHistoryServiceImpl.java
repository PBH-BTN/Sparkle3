package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.BanHistoryMapper;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.dto.PeerTrafficSummaryResultDto;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.sql.Timestamp;
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
        this.baseMapper.insert(list, 500);
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
    public @Nullable PeerTrafficSummaryResultDto sumPeerIpTraffic(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp){
        return this.baseMapper.sumPeerIpTraffic(afterTimestamp, peerIp);
    }

    @Override
    public List<Long> selectPeerTorrents(@NotNull Timestamp afterTimestamp, @NotNull InetAddress peerIp){
        return this.baseMapper.selectPeerTorrents(afterTimestamp, peerIp);
    }

}
