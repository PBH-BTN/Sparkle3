package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.SwarmDownloadStatisticsModels.SwarmDownloadCidrResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.SwarmDownloadStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/ui/statistics")
@RequiredArgsConstructor
public class SwarmDownloadStatisticsApiController {
    private final SwarmDownloadStatisticsService statisticsService;

    @GetMapping("/swarm-download-cidr")
    public SwarmDownloadCidrResponse statistics(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "32") int ipv4Prefix,
            @RequestParam(defaultValue = "56") int ipv6Prefix,
            @RequestParam(defaultValue = "all") String ipVersion,
            @RequestParam(defaultValue = "3") BigDecimal thresholdTb
    ) throws BusinessException {
        return statisticsService.getStatistics(
                hours,
                ipv4Prefix,
                ipv6Prefix,
                ipVersion,
                thresholdTb
        );
    }
}
