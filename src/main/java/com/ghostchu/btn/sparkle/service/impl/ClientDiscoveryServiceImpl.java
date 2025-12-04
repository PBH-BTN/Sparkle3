package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import com.ghostchu.btn.sparkle.mapper.ClientDiscoveryMapper;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import com.ghostchu.btn.sparkle.util.HexUtil;
import com.ghostchu.btn.sparkle.util.PeerIdParser;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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
public class ClientDiscoveryServiceImpl extends ServiceImpl<ClientDiscoveryMapper, ClientDiscovery> implements IClientDiscoveryService {

    @Transactional
    @Async
    @Override
    public void handleClientDiscovery(long userAppId, List<Pair<String, String>> data) {
        List<ClientDiscovery> clientDiscoveryList = new ArrayList<>();
        for (Pair<String, String> pair : data) {
            var peerIdIn = pair.getKey();
            var peerClientNameIn = pair.getValue();
            if (peerIdIn == null && peerClientNameIn == null) return;
            // 设置默认值
            String peerId = HexUtil.cutPeerId(HexUtil.sanitizeU0(peerIdIn));
            String peerClientName = HexUtil.sanitizeU0(peerClientNameIn);
            OffsetDateTime foundAt = OffsetDateTime.now();
            String clientType = null;
            String clientSemver = null;

            // 处理 PeerId
            if (peerIdIn != null) {
                var parsed = PeerIdParser.parse(peerIdIn);
                if (parsed != null) {
                    if (parsed.getPeerId() != null) {
                        peerId = parsed.getPeerId();
                    }
                    clientType = parsed.getClient();
                    clientSemver = parsed.getMajor() + "." + parsed.getMinor() + "." + parsed.getPatch() + "." + parsed.getHotpatch();
                }
            }

            //noinspection UnstableApiUsage
            ClientDiscovery clientDiscovery = new ClientDiscovery()
                    .setHash(Hashing.sha256().hashString(peerId + "@" + peerClientName, StandardCharsets.UTF_8).asLong())
                    .setPeerId(peerId)
                    .setPeerClientName(peerClientName)
                    .setFoundAt(foundAt)
                    .setFoundUserappsId(userAppId)
                    .setClientType(clientType)
                    .setClientSemver(clientSemver);
            clientDiscoveryList.add(clientDiscovery);
        }
        try {
            this.baseMapper.batchInsert(clientDiscoveryList);
        } catch (Exception e) {
            log.error("Error inserting client discovery data: {}", e.getMessage());
        }
    }

}
