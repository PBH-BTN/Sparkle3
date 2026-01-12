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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;

@Service
@Slf4j
public class StatisticsRefreshServiceImpl {
    private static final Timestamp thirtyDaysAgo = new Timestamp(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
    private static final Timestamp fourteenDaysAgo = new Timestamp(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000);
    private static final Timestamp sevenDaysAgo = new Timestamp(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000);
    private static final Timestamp last24HoursAgo = new Timestamp(System.currentTimeMillis() - 24L * 60 * 60 * 1000);

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

    @Value("${sparkle.statistics.tracker-dashboard-image-retrieve}")
    private String trackerDashboardImageRetrieveUrl;
    @Value("${sparkle.statistics.tracker-dashboard-image-auth-token}")
    private String trackerDashboardImageAuthToken;

    @Scheduled(cron = "${sparkle.statistics.ban-count-refresh-cron}")
    public void onBanCountRefresh() {
        long allTime = banHistoryMapper.countAll();
        long last30Days = banHistoryMapper.selectCount(new QueryWrapper<BanHistory>().ge("insert_time", thirtyDaysAgo));
        long last14Days = banHistoryMapper.selectCount(new QueryWrapper<BanHistory>().ge("insert_time", fourteenDaysAgo));
        long last7Days = banHistoryMapper.selectCount(new QueryWrapper<BanHistory>().ge("insert_time", sevenDaysAgo));
        long last24Hours = banHistoryMapper.selectCount(new QueryWrapper<BanHistory>().ge("insert_time", last24HoursAgo));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_ALLTIME.getKey(), allTime);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_30DAYS.getKey(), last30Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_14DAYS.getKey(), last14Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_7DAYS.getKey(), last7Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_BANHISTORY_24HOURS.getKey(), last24Hours);
    }

    @Scheduled(cron = "${sparkle.statistics.swarm-tracker-count-refresh-cron}")
    public void onSwarmTracker() {
        long allTime = swarmTrackerMapper.countAll();
        //long last30Days = swarmTrackerMapper.selectCount(new QueryWrapper<SwarmTracker>().ge("last_time_seen", thirtyDaysAgo));
        //long last14Days = swarmTrackerMapper.selectCount(new QueryWrapper<SwarmTracker>().ge("last_time_seen", fourteenDaysAgo));
        long last7Days = swarmTrackerMapper.selectCount(new QueryWrapper<SwarmTracker>().ge("last_time_seen", sevenDaysAgo));
        long last24Hours = swarmTrackerMapper.selectCount(new QueryWrapper<SwarmTracker>().ge("last_time_seen", last24HoursAgo));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_ALLTIME.getKey(), allTime);
        //stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_30DAYS.getKey(), last30Days);
        //stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_14DAYS.getKey(), last14Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_7DAYS.getKey(), last7Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_SWARMTRACKER_24HOURS.getKey(), last24Hours);
    }

    @Scheduled(cron = "${sparkle.statistics.userapp-count-refresh-cron}")
    public void onUserAppUpdate() {
        long last30Days = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", thirtyDaysAgo));
        long last14Days = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", fourteenDaysAgo));
        long last7Days = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", sevenDaysAgo));
        long last24Hours = userappMapper.selectCount(new QueryWrapper<Userapp>().ge("last_seen_at", last24HoursAgo));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_30DAYS.getKey(), last30Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_14DAYS.getKey(), last14Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_7DAYS.getKey(), last7Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_USERAPP_24HOURS.getKey(), last24Hours);
    }

    @Scheduled(cron = "${sparkle.statistics.torrent-count-refresh-cron}")
    public void onTorrentUpdate() {
        long last30Days = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", thirtyDaysAgo));
        long last14Days = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", fourteenDaysAgo));
        long last7Days = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", sevenDaysAgo));
        long last24Hours = torrentMapper.selectCount(new QueryWrapper<Torrent>().ge("last_seen_at", last24HoursAgo));
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_30DAYS.getKey(), last30Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_14DAYS.getKey(), last14Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_7DAYS.getKey(), last7Days);
        stringLongRedisTemplate.opsForValue().set(RedisKeyConstant.STATS_TORRENT_24HOURS.getKey(), last24Hours);
    }

    @Scheduled(cron = "${sparkle.statistics.tracker-dashboard-refresh-cron}")
    public void onTrackerDashboardRefresh() {
        // download the tracker dashboard image from trackerDashboardImageRetrieveUrl
        // and save to /tmp/sparkle_tracker_dashboard.png
        // use Java 11+ HttpClient
        try (var httpClient = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(trackerDashboardImageRetrieveUrl))
                    .header("Authorization", "Bearer " + trackerDashboardImageAuthToken)
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                Files.write(Paths.get("data/sparkle_tracker_dashboard.png"), response.body());
            } else {
                log.error("Failed to download tracker dashboard image, status code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to download tracker dashboard image", e);
        }
    }
}
