package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.Torrent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface TorrentMapper extends BaseMapper<Torrent> {
    @Nullable
    Torrent findTorrentByIdentifier(@NotNull String identifier);

    @NotNull
    Torrent upsert(@NotNull Torrent torrent);
}
