package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface ClientDiscoveryMapper extends BaseMapper<ClientDiscovery> {
    void batchInsert(@Param("list") @NotNull List<ClientDiscovery> clientDiscoveries);

    @NotNull List<Long> batchInsertReturning(
            @Param("list") @NotNull List<ClientDiscovery> clientDiscoveries
    );
}
