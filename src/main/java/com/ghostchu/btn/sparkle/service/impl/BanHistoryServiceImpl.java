package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.BanHistoryMapper;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.btnability.SparkleBtnAbility;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
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
            return new BanHistory()
                    .setInsertTime(LocalDateTime.now().atOffset(ZoneOffset.UTC))
                    .setPopulateTime(btnBan.getBanAt().toLocalDateTime().atOffset(ZoneOffset.UTC))
                    .setUserappsId(userAppId)
                    .setTorrentId(torrentService.getOrCreateTorrentId(btnBan.getTorrentIdentifier(), btnBan.getTorrentSize(), btnBan.isTorrentIsPrivate(), null, null))
                    .setPeerIp(InetAddress.ofLiteral(btnBan.getPeerIp()))
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
                    .setStructuredData(structuredDataMap);
        }).toList();
        this.baseMapper.insert(list, 500);
    }

    @Component
    @Data
    public static class SyncBanHistoryBtnAbility implements SparkleBtnAbility {
        @Value("${sparkle.ping.sync-banhistory.endpoint}")
        private String endpoint;
        @Value("${sparkle.ping.sync-banhistory.interval}")
        private long interval;
        @Value("${sparkle.ping.sync-banhistory.random-initial-delay}")
        @JsonProperty("random_initial_delay")
        private long randomInitialDelay;
        @Value("${sparkle.ping.sync-banhistory.pow-captcha}")
        @JsonProperty("pow_captcha")
        private boolean powCaptcha;

        @Override
        public String getConfigKey() {
            return "submit_bans";
        }
    }
}
