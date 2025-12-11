package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.Torrent;
import com.ghostchu.btn.sparkle.mapper.TorrentMapper;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
public class TorrentServiceImpl extends ServiceImpl<TorrentMapper, Torrent> implements ITorrentService {
    @Autowired
    @Qualifier("stringLongRedisTemplate")
    private RedisTemplate<String, Long> torrentIdRedisTemplate;

    private final com.google.common.cache.Cache<String, Long> torrentIdLocalCache = CacheBuilder.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Override
    public @Nullable Torrent getTorrentByTorrentIdentifier(@NotNull String torrentIdentifier){
        return baseMapper.findTorrentByIdentifier(torrentIdentifier);
    }

    @Override
    public long getOrCreateTorrentId(@NotNull String identifier, @Nullable Long size, @Nullable Boolean isPrivate, @Nullable String infoHash, @Nullable String torrentName) {
        // Try local cache first
        Long cachedId = torrentIdLocalCache.getIfPresent(identifier);
        if (cachedId != null) {
            return cachedId;
        }

        // Try Redis and database
        Long torrentId = loadTorrentIdFromRedisOrDb(identifier);
        if (torrentId != null) {
            // Update local cache
            torrentIdLocalCache.put(identifier, torrentId);
            return torrentId;
        }

        // Not found, create new torrent
        Torrent createNewTorrent = new Torrent()
                .setTorrentIdentifier(identifier)
                .setSize(size != null ? size : 0)
                .setPrivateTorrent(isPrivate)
                .setInfoHash(infoHash)
                .setTorrentName(torrentName)
                .setLastSeenAt(OffsetDateTime.now());
        createNewTorrent = baseMapper.upsert(createNewTorrent);
        torrentId = createNewTorrent.getId();

        // Update all cache layers
        torrentIdRedisTemplate.opsForValue().set("sparkle:torrent:" + identifier, torrentId, 30, TimeUnit.MINUTES);
        torrentIdLocalCache.put(identifier, torrentId);

        return torrentId;
    }

    private @Nullable Long loadTorrentIdFromRedisOrDb(@NotNull String identifier) {
        // Try Redis cache
        Long cachedTorrentId = torrentIdRedisTemplate.opsForValue().get("sparkle:torrent:" + identifier);
        if (cachedTorrentId != null) {
            return cachedTorrentId;
        }

        // Try database
        Torrent torrent = baseMapper.findTorrentByIdentifier(identifier);
        if (torrent != null) {
            Long torrentId = torrent.getId();
            torrentIdRedisTemplate.opsForValue().set("sparkle:torrent:" + identifier, torrentId, 30, TimeUnit.MINUTES);
            return torrentId;
        }

        // Not found
        return null;
    }
}
