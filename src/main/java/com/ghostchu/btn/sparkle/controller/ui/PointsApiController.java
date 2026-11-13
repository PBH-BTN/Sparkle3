package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsLogPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.PointsModels.PointsOverview;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.points.PointsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/points")
@RequiredArgsConstructor
public class PointsApiController {
    private final PointsQueryService pointsQueryService;

    @GetMapping("/overview")
    public PointsOverview overview(
            @AuthenticationPrincipal SparkleUserDetails userDetails
    ) {
        return pointsQueryService.overview(userDetails.getUserId());
    }

    @GetMapping("/logs")
    public PointsLogPage logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SparkleUserDetails userDetails
    ) {
        return pointsQueryService.logs(userDetails.getUserId(), page, size);
    }
}
