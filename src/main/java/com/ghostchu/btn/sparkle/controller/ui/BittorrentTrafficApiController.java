package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.BittorrentTrafficModels.HomeBittorrentTrafficResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.BittorrentTrafficStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/statistics")
@RequiredArgsConstructor
public class BittorrentTrafficApiController {
    private final BittorrentTrafficStatisticsService statisticsService;

    @GetMapping("/bittorrent-traffic")
    public HomeBittorrentTrafficResponse traffic(
            @RequestParam(defaultValue = "7") int days
    ) throws BusinessException {
        return statisticsService.getStatistics(days);
    }
}
