package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.HomepageIpFlagModels.DailyIpFlags;
import com.ghostchu.btn.sparkle.service.HomepageIpFlagService;
import com.ghostchu.btn.sparkle.service.HomepageStatisticsRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class HomepageIpFlagController {
    private final HomepageIpFlagService service;
    private final HomepageStatisticsRefreshService refreshService;

    @GetMapping("/api/ui/home/ip-flags")
    public List<DailyIpFlags> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!refreshService.isIpFlagsEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 90
                || to.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return service.daily(from, to);
    }
}
