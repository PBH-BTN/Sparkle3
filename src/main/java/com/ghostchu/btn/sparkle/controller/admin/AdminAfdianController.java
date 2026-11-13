package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.ImportRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.ImportStatus;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.PendingLicense;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.MatchRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SettingsUpdateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SyncResult;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AfdianIntegrationService;
import com.ghostchu.btn.sparkle.service.AfdianMessageImportService;
import com.ghostchu.btn.sparkle.service.AfdianMessageLicenseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/afdian")
@RequiredArgsConstructor
public class AdminAfdianController {
    private final AfdianIntegrationService afdianService;
    private final AfdianMessageImportService messageImportService;
    private final AfdianMessageLicenseWriter messageLicenseWriter;

    @GetMapping("/message-license-import/pending")
    public List<PendingLicense> pendingImports() {
        return messageLicenseWriter.pending();
    }

    @PostMapping("/message-license-import/match")
    public void matchImport(
            @RequestBody MatchRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        messageLicenseWriter.resolve(request, principal == null ? null : principal.getUserId());
    }

    @GetMapping("/message-license-import")
    public ImportStatus importStatus() {
        return messageImportService.status();
    }

    @PostMapping("/message-license-import")
    public ImportStatus importMessages(
            @RequestBody ImportRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return messageImportService.start(request, principal == null ? null : principal.getUserId());
    }

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
