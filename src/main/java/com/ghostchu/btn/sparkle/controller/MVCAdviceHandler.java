package com.ghostchu.btn.sparkle.controller;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticsService;
import com.ghostchu.btn.sparkle.util.UnitConverter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class MVCAdviceHandler {
    @Autowired
    private IUserSwarmStatisticsService userSwarmStatisticsService;
    @ModelAttribute("sessionUser")
    public User addUserToModel(HttpServletRequest request, @AuthenticationPrincipal SparkleUserDetails userDetails) {
        if(userDetails != null) {
            return userDetails.getUser();
        }
        return null;
    }

    @ModelAttribute("sessionUserSwarmStatistics")
    public UserSwarmStatistic addUserSwarmStatisticsToModel(HttpServletRequest request, @AuthenticationPrincipal SparkleUserDetails userDetails) {
        if(userDetails != null) {
            var user = userDetails.getUser();
            var swarmStats = userSwarmStatisticsService.getById(user.getId());
            if(swarmStats != null){
                return swarmStats;
            }
        }
        return new UserSwarmStatistic();
    }

    @ModelAttribute("unitConverter")
    public UnitConverter addUnitConverterToModel() {
        return UnitConverter.INSTANCE;
    }
}
