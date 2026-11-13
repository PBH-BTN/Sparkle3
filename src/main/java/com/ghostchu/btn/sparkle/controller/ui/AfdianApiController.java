package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.BindRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.BindingView;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseAvailabilityView;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AfdianIntegrationService;
import com.ghostchu.btn.sparkle.service.PbhLicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ui/afdian")
@RequiredArgsConstructor
public class AfdianApiController {
    private final AfdianIntegrationService afdianService;
    private final PbhLicenseService licenseService;

    @GetMapping
    public BindingView binding(@AuthenticationPrincipal SparkleUserDetails principal) {
        return afdianService.bindingForUser(userId(principal));
    }

    @PostMapping("/bind")
    public BindingView bind(
            @RequestBody BindRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return afdianService.bind(
                userId(principal),
                request == null ? null : request.orderNo()
        );
    }

    @DeleteMapping
    public void unbind(@AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        afdianService.unbind(userId(principal));
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
