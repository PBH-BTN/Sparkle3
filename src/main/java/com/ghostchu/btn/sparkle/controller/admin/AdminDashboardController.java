package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.DashboardResponse;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.JvmInfo;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.ModuleMetric;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.SystemSample;
import com.ghostchu.btn.sparkle.service.AdminMetricsService;
import com.ghostchu.btn.sparkle.service.IRuleService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminMetricsService metricsService;
    private final IUserService userService;
    private final IUserappService userappService;
    private final IRuleService ruleService;
    private final RedisTemplate<String, Long> redisTemplate;

    public AdminDashboardController(AdminMetricsService metricsService,
                                    IUserService userService,
                                    IUserappService userappService,
                                    IRuleService ruleService,
                                    @Qualifier("stringLongRedisTemplate") RedisTemplate<String, Long> redisTemplate) {
        this.metricsService = metricsService;
        this.userService = userService;
        this.userappService = userappService;
        this.ruleService = ruleService;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        SystemSample current = metricsService.currentSample();
        List<SystemSample> history = new ArrayList<>(metricsService.history(Duration.ofDays(7)));
        if (history.isEmpty()
                || current.timestamp() - history.getLast().timestamp() > Duration.ofMinutes(2).toMillis()) {
            history.add(current);
        }

        Runtime runtime = Runtime.getRuntime();
        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        JvmInfo jvm = new JvmInfo(
                runtime.availableProcessors(),
                ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),
                ManagementFactory.getRuntimeMXBean().getUptime(),
                runtime.totalMemory() - runtime.freeMemory(),
                runtime.maxMemory(),
                threadCount,
                System.getProperty("java.version")
        );

        List<ModuleMetric> modules = List.of(
                new ModuleMetric("users", userService.count()),
                new ModuleMetric("userApps", userappService.count()),
                new ModuleMetric("manualRules", ruleService.count()),
                new ModuleMetric("bans24h", read(RedisKeyConstant.STATS_BANHISTORY_24HOURS)),
                new ModuleMetric("activeApps24h", read(RedisKeyConstant.STATS_USERAPP_24HOURS)),
                new ModuleMetric("swarms24h", read(RedisKeyConstant.STATS_SWARMTRACKER_24HOURS))
        );

        return new DashboardResponse(current, history, modules, jvm, OffsetDateTime.now());
    }

    private long read(RedisKeyConstant key) {
        Long value = redisTemplate.opsForValue().get(key.getKey());
        return value == null ? 0 : value;
    }
}
