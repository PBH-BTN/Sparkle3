package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.service.impl.UserSwarmStatisticsServiceImpl;
import jakarta.websocket.server.PathParam;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.OffsetDateTime;
import java.util.StringJoiner;


@Controller
public class SwarmStatisticDebugController {

    @Autowired
    private UserSwarmStatisticsServiceImpl userSwarmStatisticsService;

    @GetMapping("/debug/swarmStatistic/perUser/{uid}")
    public ResponseEntity<@NotNull String> debugPerUserSwarmStatistic(@PathVariable("uid") Long uid) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("DEBUG: Swarm Statistic for User " + uid);
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        OffsetDateTime now = OffsetDateTime.now();
        var result = userSwarmStatisticsService.generateUserSwarmStatistics(uid, sevenDaysAgo, now);
        joiner.add("Result: " + result);
        return ResponseEntity.ok(joiner.toString());
    }
}
