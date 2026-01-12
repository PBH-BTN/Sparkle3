package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.GithubSyncServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;


@Controller
@RequestMapping("/debug/githubSync")
public class GithubSyncDebugController {

    @Autowired
    private GithubSyncServiceImpl service;


    @GetMapping("/executeScheduleSyncCron")
    public ResponseEntity<@NotNull String> executeScheduleSyncCron() throws IOException {
        service.scheduleSync();
        return ResponseEntity.ok("OK!");
    }
}

