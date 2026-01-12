package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.service.impl.GithubSyncServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.SwarmTrackerServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;


@Controller
@RequestMapping("/debug/swarmTracker")
public class SwarmTrackerDebugController {

    @Autowired
    private SwarmTrackerServiceImpl service;


    @GetMapping("/executeDataRetentionCleanup")
    public ResponseEntity<@NotNull String> executeDataRetentionCleanup() throws IOException {
        service.cronDataRetentionCleanup();
        return ResponseEntity.ok("OK!");
    }
}

