package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IClientDiscoveryService extends IService<ClientDiscovery> {

    void handleClientDiscovery(long userAppId, List<Pair<String, String>> data);

    /**
     * 查询客户端发现记录
     *
     * @param peerId         Peer ID（支持模糊查询）
     * @param peerClientName Peer 客户端名称（支持模糊查询）
     * @param clientType     客户端类型（精确匹配）
     * @param clientSemver   客户端语义版本（支持模糊查询）
     * @param sortBy         排序字段
     * @param sortOrder      排序方向 ("asc" 或 "desc")
     * @param page           分页对象
     * @return 分页结果
     */
    @NotNull
    IPage<ClientDiscovery> queryClientDiscovery(
            @Nullable String peerId,
            @Nullable String peerClientName,
            @Nullable String clientType,
            @Nullable String clientSemver,
            @Nullable String sortBy,
            @Nullable String sortOrder,
            @NotNull Page<ClientDiscovery> page
    );

    /**
     * 获取所有不同的客户端类型列表
     *
     * @return 客户端类型列表
     */
    @NotNull
    List<String> getAllClientTypes();
}
