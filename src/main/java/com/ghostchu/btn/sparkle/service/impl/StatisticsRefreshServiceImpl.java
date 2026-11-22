package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.mapper.BanHistoryMapper;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.mapper.TorrentMapper;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import com.ghostchu.btn.sparkle.service.dto.UniversalCountDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;

@Service
@Slf4j
public class StatisticsRefreshServiceImpl {
    @Autowired
    @Qualifier("stringLongRedisTemplate")
    private RedisTemplate<String, Long> stringLongRedisTemplate;
    @Autowired
    private BanHistoryMapper banHistoryMapper;
    @Autowired
    private SwarmTrackerMapper swarmTrackerMapper;
    @Autowired
    private UserappMapper userappMapper;
    @Autowired
    private TorrentMapper torrentMapper;

    public void onBanCountRefresh() {
        long allTime = banHistoryMapper.estimateCountAll();
        UniversalCountDto count = banHistoryMapper.countTimeStatistics();
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_ALLTIME.getKey(), allTime);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_30DAYS.getKey(), count.getDays30());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_14DAYS.getKey(), count.getDays14());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_7DAYS.getKey(), count.getDays7());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_24HOURS.getKey(), count.getDays1());
    }

    public void onSwarmTracker() {
        long allTime = swarmTrackerMapper.estimateCountAll();
        UniversalCountDto count = swarmTrackerMapper.countTimeStatistics();
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_ALLTIME.getKey(), allTime);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_30DAYS.getKey(), count.getDays30());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_14DAYS.getKey(), count.getDays14());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_7DAYS.getKey(), count.getDays7());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_24HOURS.getKey(), count.getDays1());
    }

    public void onUserAppUpdate() {
        var count = userappMapper.countApplications(Instant.ofEpochMilli(System.currentTimeMillis()).atOffset(ZoneOffset.UTC));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_ALLTIME.getKey(), count.allTime());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_30DAYS.getKey(), count.days30());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_14DAYS.getKey(), count.days14());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_7DAYS.getKey(), count.days7());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_24HOURS.getKey(), count.days1());
    }

    public void onTorrentUpdate() {
        var count = torrentMapper.countTimeStatistics(Instant.ofEpochMilli(System.currentTimeMillis()).atOffset(ZoneOffset.UTC));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_30DAYS.getKey(), count.getDays30());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_14DAYS.getKey(), count.getDays14());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_7DAYS.getKey(), count.getDays7());
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_24HOURS.getKey(), count.getDays1());
    }

}
