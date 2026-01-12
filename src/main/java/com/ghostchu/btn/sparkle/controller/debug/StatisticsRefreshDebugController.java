package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.service.impl.StatisticsRefreshServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/debug/statisticsRefresh")
public class StatisticsRefreshDebugController {

    @Autowired
    private StatisticsRefreshServiceImpl service;


    @GetMapping("/executeBanCountRefreshCron")
    public ResponseEntity<@NotNull String> executeBanCountRefreshCron() {
        service.onBanCountRefresh();
        return ResponseEntity.ok("OK!");
    }

    @GetMapping("/executeSwarmTrackerRefreshCron")
    public ResponseEntity<@NotNull String> executeSwarmTrackerRefreshCron() {
        service.onSwarmTracker();
        return ResponseEntity.ok("OK!");
    }

    @GetMapping("/executeUserAppleRefreshCron")
    public ResponseEntity<@NotNull String> executeUserAppleRefreshCron() {
        service.onUserAppUpdate();
        return ResponseEntity.ok("OK!");
    }

    @GetMapping("/executeTorrentUpdate")
    public ResponseEntity<@NotNull String> executeTorrentUpdate() {
        service.onTorrentUpdate();
        return ResponseEntity.ok("OK!");
    }

    @GetMapping("/executeTrackerDashboardRefreshCron")
    public ResponseEntity<@NotNull String> executeTrackerDashboardRefreshCron() {
        service.onTrackerDashboardRefresh();
        return ResponseEntity.ok("OK!");
    }
}

