package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.entity.GeoIpCityBlacklistEntry;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.GeoIpCityBlacklistService;
import com.ghostchu.btn.sparkle.service.GeoIpCityBlacklistService.GenerationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/geoip-city-blacklist")
@RequiredArgsConstructor
public class AdminGeoIpCityBlacklistController {
    private final GeoIpCityBlacklistService service;

    @GetMapping
    public List<GeoIpCityBlacklistEntry> list() {
        return service.listEntries();
    }

    @GetMapping("/status")
    public GenerationStatus status() {
        return service.status();
    }

    @PostMapping("/refresh")
    public GenerationStatus refresh(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return service.requestRefresh(operatorId(principal));
    }

    @GetMapping("/{id}/generated-content")
    public GeneratedContentResponse generatedContent(@PathVariable long id)
            throws BusinessException {
        return new GeneratedContentResponse(service.getGeneratedEntryContent(id));
    }

    @PostMapping
    public GeoIpCityBlacklistEntry create(
            @RequestBody EntryRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return service.createEntry(
                request.countryCode(),
                request.province(),
                request.city(),
                request.enabled(),
                operatorId(principal)
        );
    }

    @PutMapping("/{id}")
    public GeoIpCityBlacklistEntry update(
            @PathVariable long id,
            @RequestBody EntryRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return service.updateEntry(
                id,
                request.countryCode(),
                request.province(),
                request.city(),
                request.enabled(),
                operatorId(principal)
        );
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        service.deleteEntry(id, operatorId(principal));
    }

    private Long operatorId(SparkleUserDetails principal) {
        return principal == null ? null : principal.getUserId();
    }

    public record EntryRequest(
            String countryCode,
            String province,
            String city,
            boolean enabled
    ) {
    }

    public record GeneratedContentResponse(String content) {
    }
}
