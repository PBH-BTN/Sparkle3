package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.HomeStatisticsModels.HomeStatisticsResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.HomeStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/statistics")
@RequiredArgsConstructor
public class HomeStatisticsApiController {
    private final HomeStatisticsService statisticsService;

    @GetMapping("/home")
    public HomeStatisticsResponse home(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "ban") String source
    ) throws BusinessException {
        return statisticsService.get(days, source);
    }
}
