package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.IPDBMeasure;
import com.ghostchu.btn.sparkle.entity.Torrent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IPDBMeasureMapper extends BaseMapper<IPDBMeasure> {

    @Nullable IPDBMeasure findClosestSuccessMeasure(@NotNull String address);

    @Nullable IPDBMeasure findClosestMeasure(@NotNull String address);

    @NotNull List<IPDBMeasure> findMeasures(@NotNull String address);

    @NotNull List<IPDBMeasure> findStartedUnfinishedMeasures( int limit);
}
