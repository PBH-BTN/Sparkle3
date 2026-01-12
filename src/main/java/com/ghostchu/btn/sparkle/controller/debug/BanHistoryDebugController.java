package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.UserSwarmStatisticsServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.OffsetDateTime;
import java.util.StringJoiner;


@Controller
@RequestMapping("/debug/banHistory")
public class BanHistoryDebugController {

    @Autowired
    private BanHistoryServiceImpl banHistoryService;


    @GetMapping("/executeRefreshDistinctModuleNamesCacheCron")
    public ResponseEntity<@NotNull String> executeRefreshDistinctModuleNamesCacheCron() {
        banHistoryService.refreshDistinctModuleNamesCache();
        return ResponseEntity.ok("OK!");
    }
}

