package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {
    private final AdminConfigService configService;

    @GetMapping
    public RuntimeConfigView get() {
        return configService.get();
    }

    @PutMapping
    public RuntimeConfigView update(@RequestBody RuntimeConfigView config,
                                    @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        return configService.update(config, principal == null ? null : principal.getUserId());
    }
}
