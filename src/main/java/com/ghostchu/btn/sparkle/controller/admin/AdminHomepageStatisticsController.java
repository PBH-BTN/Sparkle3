package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.AdminResponse;
import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.ConfigUpdate;
import com.ghostchu.btn.sparkle.controller.admin.dto.HomepageStatisticsModels.RefreshRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.HomepageStatisticsRefreshService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/homepage-statistics")
@RequiredArgsConstructor
public class AdminHomepageStatisticsController {
    private final HomepageStatisticsRefreshService refreshService;

    @GetMapping
    public AdminResponse status() {
        return refreshService.getStatus();
    }

    @PutMapping("/config")
    public AdminResponse updateConfig(
            @RequestBody @NotNull ConfigUpdate update,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return refreshService.updateConfig(
                update,
                principal == null ? null : principal.getUserId()
        );
    }

    @PostMapping("/refresh")
    public AdminResponse refresh(
            @RequestBody(required = false) RefreshRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return refreshService.requestRefresh(
                request == null ? null : request.module(),
                principal == null ? null : principal.getUserId()
        );
    }
}
