package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.PbhLicenseRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/memberships/license-revocations")
@RequiredArgsConstructor
public class AdminLicenseRevocationController {
    private final PbhLicenseRevocationService service;

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @PostMapping
    public void revoke(@RequestBody RevokeRequest request,
                       @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        service.revoke(request.licenseId(), request.licenseKey(), request.reason(), principal.getUserId());
    }

    public record RevokeRequest(UUID licenseId, String licenseKey, String reason) {
        @Override
        public String toString() { return "RevokeRequest[redacted]"; }
    }
}
