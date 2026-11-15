package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.TrafficPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.DailyTraffic;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.UserTrafficStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ui/user/traffic")
public class UserTrafficStatisticsController {
    private final UserTrafficStatisticsService service;

    @GetMapping
    public TrafficPage daily(@RequestParam(defaultValue="1") long page,
                                    @RequestParam(defaultValue="30") int size,
                                    @AuthenticationPrincipal SparkleUserDetails principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return service.daily(principal.getUserId(),page,size);
    }

    @GetMapping("/network")
    public List<DailyTraffic> network() {
        return service.network();
    }
}
