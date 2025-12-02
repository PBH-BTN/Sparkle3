package com.ghostchu.btn.sparkle.service;

import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.Torrent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface ITorrentService extends IService<Torrent> {

    @Lock4j(keys = {"#identifier"})
    long getOrCreateTorrentId(@NotNull String identifier, @Nullable Long size, @Nullable Boolean isPrivate, @Nullable String infoHash, @Nullable String torrentName);
}
