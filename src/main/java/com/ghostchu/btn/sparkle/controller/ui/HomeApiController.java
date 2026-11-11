package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.HomeStats;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.SessionResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.TrafficSummary;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.UserView;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticsService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.NavigationService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ui")
public class HomeApiController {
    private final RedisTemplate<String, Long> redisTemplate;
    private final IUserSwarmStatisticsService userSwarmStatisticsService;
    private final IUserService userService;
    private final AdminConfigService adminConfigService;
    private final NavigationService navigationService;
    private final PointsService pointsService;

    public HomeApiController(@Qualifier("stringLongRedisTemplate") RedisTemplate<String, Long> redisTemplate,
                             IUserSwarmStatisticsService userSwarmStatisticsService,
                             IUserService userService,
                             AdminConfigService adminConfigService,
                             NavigationService navigationService,
                             PointsService pointsService) {
        this.redisTemplate = redisTemplate;
        this.userSwarmStatisticsService = userSwarmStatisticsService;
        this.userService = userService;
        this.adminConfigService = adminConfigService;
        this.navigationService = navigationService;
        this.pointsService = pointsService;
    }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal SparkleUserDetails userDetails) {
        if (userDetails == null) {
            return SessionResponse.anonymous();
        }
        var user = userService.getById(userDetails.getUserId());
        if (user == null) {
            return SessionResponse.anonymous();
        }
        try {
            pointsService.awardDailyLogin(user.getId());
        } catch (Exception ignored) {
            // A points failure must not prevent the session from loading.
        }
        var statisticsConfig = adminConfigService.get();
        var navigation = navigationService.listPublic();
        if (!statisticsConfig.swarmStatisticsEnabled()) {
            return new SessionResponse(true, UserView.from(user), null, navigation);
        }
        var statistics = userSwarmStatisticsService.getById(user.getId());
        return new SessionResponse(
                true,
                UserView.from(user),
                TrafficSummary.from(
                        statistics,
                        statisticsConfig.swarmStatisticsRefreshMinutes(),
                        statisticsConfig.swarmStatisticsWindowDays()
                ),
                navigation
        );
    }

    @GetMapping("/home")
    public HomeStats home() {
        return new HomeStats(
                readStats(
                        RedisKeyConstant.STATS_BANHISTORY_ALLTIME,
                        RedisKeyConstant.STATS_BANHISTORY_30DAYS,
                        RedisKeyConstant.STATS_BANHISTORY_14DAYS,
                        RedisKeyConstant.STATS_BANHISTORY_7DAYS,
                        RedisKeyConstant.STATS_BANHISTORY_24HOURS
                ),
                readStats(
                        RedisKeyConstant.STATS_USERAPP_ALLTIME,
                        RedisKeyConstant.STATS_USERAPP_30DAYS,
                        RedisKeyConstant.STATS_USERAPP_14DAYS,
                        RedisKeyConstant.STATS_USERAPP_7DAYS,
                        RedisKeyConstant.STATS_USERAPP_24HOURS
                ),
                readStats(
                        RedisKeyConstant.STATS_SWARMTRACKER_ALLTIME,
                        RedisKeyConstant.STATS_SWARMTRACKER_30DAYS,
                        RedisKeyConstant.STATS_SWARMTRACKER_14DAYS,
                        RedisKeyConstant.STATS_SWARMTRACKER_7DAYS,
                        RedisKeyConstant.STATS_SWARMTRACKER_24HOURS
                )
        );
    }

    private Map<String, Long> readStats(RedisKeyConstant allTime,
                                        RedisKeyConstant last30Days,
                                        RedisKeyConstant last14Days,
                                        RedisKeyConstant last7Days,
                                        RedisKeyConstant last24Hours) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("allTime", read(allTime));
        stats.put("last30Days", read(last30Days));
        stats.put("last14Days", read(last14Days));
        stats.put("last7Days", read(last7Days));
        stats.put("last24Hours", read(last24Hours));
        return stats;
    }

    private long read(RedisKeyConstant key) {
        Long value = redisTemplate.opsForValue().get(key.getKey());
        return value == null ? 0 : value;
    }
}
