package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.Torrent;
import com.ghostchu.btn.sparkle.mapper.TorrentMapper;
import com.ghostchu.btn.sparkle.service.ITorrentService;
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

    @Override
    public @Nullable Torrent getTorrentByTorrentIdentifier(@NotNull String torrentIdentifier){
        return baseMapper.findTorrentByIdentifier(torrentIdentifier);
    }

    // all parmas as lock4j keys
    @Override
    public long getOrCreateTorrentId(@NotNull String identifier, @Nullable Long size, @Nullable Boolean isPrivate, @Nullable String infoHash, @Nullable String torrentName) {
        Long cachedTorrentId = torrentIdRedisTemplate.opsForValue().get(identifier);
        if (cachedTorrentId != null) return cachedTorrentId;
        Torrent torrent = baseMapper.findTorrentByIdentifier(identifier);
        if (torrent != null) {
            torrentIdRedisTemplate.opsForValue().set("sparkle:torrent:" + identifier, torrent.getId(), 30, TimeUnit.MINUTES);
            return torrent.getId();
        }
        Torrent createNewTorrent = new Torrent()
                .setTorrentIdentifier(identifier)
                .setSize(size != null ? size : 0)
                .setPrivateTorrent(isPrivate)
                .setInfoHash(infoHash)
                .setTorrentName(torrentName)
                .setLastSeenAt(OffsetDateTime.now());
        createNewTorrent = baseMapper.upsert(createNewTorrent);
        torrentIdRedisTemplate.opsForValue().set("sparkle:torrent:" + identifier, createNewTorrent.getId(), 30, TimeUnit.MINUTES);
        return createNewTorrent.getId();
    }
}
