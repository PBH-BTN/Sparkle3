package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.FlarumIntegrationAdminModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.FlarumIntegrationAdminModels.UpdateTokenRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.FlarumIntegrationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/flarum-integration")
@RequiredArgsConstructor
public class AdminFlarumIntegrationController {
    private final FlarumIntegrationSettingsService settingsService;

    @GetMapping
    public SettingsView get() {
        return settingsService.view();
    }

    @PutMapping("/token")
    public SettingsView updateToken(
            @RequestBody UpdateTokenRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return settingsService.update(
                request.token(),
                Boolean.TRUE.equals(request.clearToken()),
                principal == null ? null : principal.getUserId()
        );
    }
}
