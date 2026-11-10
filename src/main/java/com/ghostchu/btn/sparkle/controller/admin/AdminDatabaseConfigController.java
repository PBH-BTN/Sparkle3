package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigEntry;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigSummary;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.MigrationResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.UpdateConfigRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.DatabaseConfigMigrationService;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/database-config")
@RequiredArgsConstructor
public class AdminDatabaseConfigController {
    private final DatabaseConfigService configService;
    private final DatabaseConfigMigrationService migrationService;

    @GetMapping
    public List<ConfigEntry> list(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String group) {
        return configService.list(keyword, group);
    }

    @GetMapping("/summary")
    public ConfigSummary summary() {
        return configService.summary();
    }

    @PostMapping("/migrate")
    public MigrationResult migrate() {
        return migrationService.migrateNow();
    }

    @PutMapping
    public ConfigEntry update(@RequestBody UpdateConfigRequest request,
                              @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        return configService.update(
                request.key(),
                request.value(),
                principal == null ? null : principal.getUserId()
        );
    }
}
