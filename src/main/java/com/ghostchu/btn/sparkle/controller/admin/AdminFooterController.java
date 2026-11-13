package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterConfigView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.FooterConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/footer-config")
@RequiredArgsConstructor
public class AdminFooterController {
    private final FooterConfigService footerConfigService;

    @GetMapping
    public FooterConfigView get() {
        return footerConfigService.get();
    }

    @PutMapping
    public FooterConfigView update(@RequestBody FooterConfigView config,
                                   @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        return footerConfigService.update(
                config,
                principal == null ? null : principal.getUserId()
        );
    }
}
