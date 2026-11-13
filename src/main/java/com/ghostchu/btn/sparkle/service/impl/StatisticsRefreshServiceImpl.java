package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.entity.Torrent;
import com.ghostchu.btn.sparkle.entity.Userapp;
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

import java.sql.Timestamp;
import java.time.Duration;

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
        Timestamp thirtyDaysAgo = ago(Duration.ofDays(30));
        Timestamp fourteenDaysAgo = ago(Duration.ofDays(14));
        Timestamp sevenDaysAgo = ago(Duration.ofDays(7));
        Timestamp last24HoursAgo = ago(Duration.ofDays(1));
        long allTime = userappMapper.selectCount(null);
        long last30Days = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", thirtyDaysAgo));
        long last14Days = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", fourteenDaysAgo));
        long last7Days = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", sevenDaysAgo));
        long last24Hours = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", last24HoursAgo));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_ALLTIME.getKey(), allTime);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_30DAYS.getKey(), last30Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_14DAYS.getKey(), last14Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_7DAYS.getKey(), last7Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_24HOURS.getKey(), last24Hours);
    }

    public void onTorrentUpdate() {
        Timestamp thirtyDaysAgo = ago(Duration.ofDays(30));
        Timestamp fourteenDaysAgo = ago(Duration.ofDays(14));
        Timestamp sevenDaysAgo = ago(Duration.ofDays(7));
        Timestamp last24HoursAgo = ago(Duration.ofDays(1));
        long last30Days = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", thirtyDaysAgo));
        long last14Days = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", fourteenDaysAgo));
        long last7Days = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", sevenDaysAgo));
        long last24Hours = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", last24HoursAgo));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_30DAYS.getKey(), last30Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_14DAYS.getKey(), last14Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_7DAYS.getKey(), last7Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_24HOURS.getKey(), last24Hours);
    }

    private Timestamp ago(Duration duration) {
        return new Timestamp(System.currentTimeMillis() - duration.toMillis());
    }

}
