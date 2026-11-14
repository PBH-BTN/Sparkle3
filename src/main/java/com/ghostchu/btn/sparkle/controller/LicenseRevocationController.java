package com.ghostchu.btn.sparkle.controller;

import com.ghostchu.btn.sparkle.service.PbhLicenseRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LicenseRevocationController {
    private final PbhLicenseRevocationService service;

    @GetMapping("/peerbanhelper/v1/licenses/checkRevoke")
    public Map<String, Boolean> check(@RequestParam Map<String, String> params) {
        return Map.of("revoked", service.check(params));
    }
}
