package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseAvailabilityView;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MemberPlatformPublicModels.BindRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MemberPlatformPublicModels.ProviderBindingView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AfdianIntegrationService;
import com.ghostchu.btn.sparkle.service.MemberPlatformService;
import com.ghostchu.btn.sparkle.service.PbhLicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ui/memberships")
@RequiredArgsConstructor
public class MemberPlatformApiController {
    private final MemberPlatformService memberPlatformService;
    private final AfdianIntegrationService afdianIntegrationService;
    private final PbhLicenseService licenseService;

    @GetMapping
    public List<ProviderBindingView> list(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return memberPlatformService.userProviders(userId(principal));
    }

    @PostMapping("/{provider}/bind")
    public ProviderBindingView bind(
            @PathVariable String provider,
            @RequestBody BindRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        MemberPlatform platform = memberPlatformService.parseProvider(provider);
        long userId = userId(principal);
        if (platform == MemberPlatform.AFDIAN) {
            afdianIntegrationService.bind(
                    userId,
                    request == null ? null : request.proof()
            );
            return memberPlatformService.bindingView(userId, platform, false);
        }
        return memberPlatformService.bind(
                userId,
                platform,
                request == null ? null : request.proof()
        );
    }

    @DeleteMapping("/{provider}")
    public void unbind(
            @PathVariable String provider,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        MemberPlatform platform = memberPlatformService.parseProvider(provider);
        if (platform == MemberPlatform.AFDIAN) {
            afdianIntegrationService.unbind(userId(principal));
            return;
        }
        memberPlatformService.unbind(userId(principal), platform);
    }

    @GetMapping("/licenses")
    public LicenseAvailabilityView licenses(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return licenseService.availability(userId(principal));
    }

    @PostMapping("/licenses")
    public LicenseView generateLicense(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return licenseService.generate(userId(principal));
    }

    private long userId(SparkleUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
