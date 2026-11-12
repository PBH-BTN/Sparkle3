package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.BtnConfigView;
import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.UpdateBtnConfigRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.btn.AdminBtnConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/btn")
@RequiredArgsConstructor
public class AdminBtnConfigController {
    private final AdminBtnConfigService configService;

    @GetMapping
    public BtnConfigView get() {
        return configService.get();
    }

    @PutMapping
    public BtnConfigView update(
            @RequestBody UpdateBtnConfigRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return configService.update(
                request.values(),
                principal == null ? null : principal.getUserId()
        );
    }
}
