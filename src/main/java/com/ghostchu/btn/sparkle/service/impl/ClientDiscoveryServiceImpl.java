package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import com.ghostchu.btn.sparkle.mapper.ClientDiscoveryMapper;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import com.ghostchu.btn.sparkle.util.HexUtil;
import com.ghostchu.btn.sparkle.util.PeerIdParser;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Value("${sparkle.client-discovery.enabled}")
    private boolean useClientDiscovery;

    @Transactional
    @Override
    public void handleClientDiscovery(long userAppId, List<Pair<String, String>> data) {
        if(!useClientDiscovery) return;
        List<ClientDiscovery> clientDiscoveryList = new ArrayList<>();
        for (Pair<String, String> pair : data) {
            var peerIdIn = pair.getKey();
            var peerClientName = pair.getValue();
            if (peerIdIn == null && peerClientName == null) return;
            // 设置默认值
            String peerId = HexUtil.cutPeerId(peerIdIn);
            OffsetDateTime foundAt = OffsetDateTime.now();
            String clientType = null;
            String clientSemver = null;

            if(peerId != null && peerId.startsWith("-XL0012") && peerClientName.startsWith("-XL0012-"))
                continue;

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
            ClientDiscovery clientDiscovery = new ClientDiscovery()
                    .setHash(Hashing.sha256().hashString(peerId + "@" + peerClientName, StandardCharsets.UTF_8).asLong())
                    .setPeerId(HexUtil.sanitizeU0(peerId))
                    .setPeerClientName(HexUtil.sanitizeU0(peerClientName))
                    .setFoundAt(foundAt)
                    .setFoundUserappsId(userAppId)
                    .setClientType(clientType)
                    .setClientSemver(clientSemver);
            clientDiscoveryList.add(clientDiscovery);
        }
        try {
            if (!clientDiscoveryList.isEmpty()) {
                this.baseMapper.batchInsert(clientDiscoveryList);
            }
        } catch (Exception e) {
            log.error("Error inserting client discovery data: {}", e.getMessage());
        }
    }

    @Override
    public @NotNull IPage<ClientDiscovery> queryClientDiscovery(
            @Nullable String peerId,
            @Nullable String peerClientName,
            @Nullable String clientType,
            @Nullable String clientSemver,
            @Nullable String sortBy,
            @Nullable String sortOrder,
            @NotNull Page<ClientDiscovery> page) {

        QueryWrapper<ClientDiscovery> wrapper = new QueryWrapper<>();

        // 添加查询条件
        wrapper.like(peerId != null && !peerId.isBlank(), "peer_id", peerId)
                .like(peerClientName != null && !peerClientName.isBlank(), "peer_client_name", peerClientName)
                .eq(clientType != null && !clientType.isBlank(), "client_type", clientType)
                .like(clientSemver != null && !clientSemver.isBlank(), "client_semver", clientSemver);

        // 添加排序
        String sort = (sortBy != null && !sortBy.isBlank()) ? sortBy : "found_at";
        String order = (sortOrder != null && !sortOrder.isBlank()) ? sortOrder : "desc";
        if ("desc".equalsIgnoreCase(order)) {
            wrapper.orderByDesc(sort);
        } else {
            wrapper.orderByAsc(sort);
        }

        return this.baseMapper.selectPage(page, wrapper);
    }

    @Override
    public @NotNull List<String> getAllClientTypes() {
        QueryWrapper<ClientDiscovery> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT client_type")
                .isNotNull("client_type")
                .orderByAsc("client_type");

        return this.baseMapper.selectList(wrapper)
                .stream()
                .map(ClientDiscovery::getClientType)
                .distinct()
                .collect(Collectors.toList());
    }

}
