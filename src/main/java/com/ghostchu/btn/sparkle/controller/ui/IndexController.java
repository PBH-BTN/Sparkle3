package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.impl.StatisticsRefreshServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器
 */
@Controller
public class IndexController extends AbstractSparkleMVC {
    @Autowired
    private StatisticsRefreshServiceImpl statisticsRefreshService;
    @Autowired
    @Qualifier("stringLongRedisTemplate")
    private RedisTemplate<String, Long> stringLongRedisTemplate;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal SparkleUserDetails userDetails, Model model) {
        // 封禁数量
        model.addAttribute("stats_banhistory_alltime", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_BANHISTORY_ALLTIME.getKey()));
        model.addAttribute("stats_banhistory_30days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_BANHISTORY_30DAYS.getKey()));
        model.addAttribute("stats_banhistory_14days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_BANHISTORY_14DAYS.getKey()));
        model.addAttribute("stats_banhistory_7days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_BANHISTORY_7DAYS.getKey()));
        model.addAttribute("stats_banhistory_24hours", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_BANHISTORY_24HOURS.getKey()));
        // 跟踪 Peers
        model.addAttribute("stats_swarmtracker_alltime", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_SWARMTRACKER_ALLTIME.getKey()));
        model.addAttribute("stats_swarmtracker_14days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_SWARMTRACKER_14DAYS.getKey()));
        model.addAttribute("stats_swarmtracker_7days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_SWARMTRACKER_7DAYS.getKey()));
        model.addAttribute("stats_swarmtracker_24hours", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_SWARMTRACKER_24HOURS.getKey()));
        // 活动 UserApps
        model.addAttribute("stats_userapp_30days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_USERAPP_30DAYS.getKey()));
        model.addAttribute("stats_userapp_14days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_USERAPP_14DAYS.getKey()));
        model.addAttribute("stats_userapp_7days", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_USERAPP_7DAYS.getKey()));
        model.addAttribute("stats_userapp_24hours", stringLongRedisTemplate.opsForValue().get(RedisKeyConstant.STATS_USERAPP_24HOURS.getKey()));
        return "index";
    }
}
