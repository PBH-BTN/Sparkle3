package com.ghostchu.btn.sparkle.controller.ui.ranking;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticsService;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class RankingSwarmStatisticsController {
    @Autowired
    private IUserService userService;
    @Autowired
    private IUserSwarmStatisticsService userSwarmStatisticsService;

    @GetMapping("/ranking/swarmStatistics")
    public String ranking(Model model, @AuthenticationPrincipal SparkleUserDetails userDetails){
        var list = userSwarmStatisticsService.getUsersRanking().stream().limit(500).toList();
        Map<UserSwarmStatisticTrackRankingDto, User> data = new LinkedHashMap<>();
        for (var item : list) {
            var user = userService.getById(item.getUserId());
            if (user != null) {
                data.put(item, user);
            }
        }
        var self = userSwarmStatisticsService.getUserRanking(userDetails.getUserId());
        model.addAttribute("data", data);
        model.addAttribute("self", self);
        return "ranking/swarmstatistics/index";
    }
}
