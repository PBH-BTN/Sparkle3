package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Settings;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.export.DataExportSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/exports")
@RequiredArgsConstructor
public class AdminDataExportController {
    private final DataExportSettings settings;

    @GetMapping
    public Settings settings() {
        return settings.get();
    }

    @PutMapping
    public Settings update(@RequestBody Settings request, @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        settings.update(request, principal == null ? null : principal.getUserId());
        return settings.get();
    }
}
