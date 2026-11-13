package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SettingsUpdateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SyncResult;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AfdianIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/afdian")
@RequiredArgsConstructor
public class AdminAfdianController {
    private final AfdianIntegrationService afdianService;

    @GetMapping
    public SettingsView settings() {
        return afdianService.settings();
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(
            @RequestBody SettingsUpdateRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian settings are required");
        }
        afdianService.updateSettings(
                request.userId(),
                request.accessToken(),
                Boolean.TRUE.equals(request.clearToken()),
                request.apiBaseUrl(),
                request.syncEnabled(),
                principal == null ? null : principal.getUserId()
        );
        return afdianService.settings();
    }

    @PostMapping("/sync")
    public SyncResult sync() throws BusinessException {
        return afdianService.syncNow();
    }
}
