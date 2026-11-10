package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticsService;
import com.ghostchu.btn.sparkle.service.dto.SwarmStatisticsStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/swarm-statistics")
@RequiredArgsConstructor
public class AdminSwarmStatisticsController {
    private final IUserSwarmStatisticsService statisticsService;

    @GetMapping("/status")
    public SwarmStatisticsStatusDto status() {
        return statisticsService.getRefreshStatus();
    }

    @PostMapping("/refresh")
    public SwarmStatisticsStatusDto refresh(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        statisticsService.requestManualRefresh(principal == null ? null : principal.getUserId());
        return statisticsService.getRefreshStatus();
    }
}
