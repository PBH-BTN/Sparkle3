package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertDelivery;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertMatch;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertRule;
import com.ghostchu.btn.sparkle.controller.admin.dto.VersionAlertModels.VersionAlertRuleInput;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.VersionAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/version-alerts")
@RequiredArgsConstructor
public class AdminVersionAlertController {
    private final VersionAlertService versionAlertService;

    @GetMapping
    public List<VersionAlertRule> list() {
        return versionAlertService.listRules();
    }

    @PostMapping
    public VersionAlertRule create(@RequestBody VersionAlertRuleInput input,
                                   @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        return versionAlertService.create(input, principal == null ? null : principal.getUserId());
    }

    @PutMapping("/{id}")
    public VersionAlertRule update(@PathVariable String id,
                                   @RequestBody VersionAlertRuleInput input,
                                   @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        return versionAlertService.update(id, input, principal == null ? null : principal.getUserId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        versionAlertService.delete(id);
    }

    @PostMapping("/{id}/match")
    public VersionAlertMatch match(@PathVariable String id, @RequestBody MatchRequest request)
            throws BusinessException {
        return versionAlertService.match(id, request.userAgent());
    }

    @PostMapping("/{id}/test")
    public VersionAlertDelivery test(@PathVariable String id,
                                     @RequestBody TestRequest request,
                                     @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        return versionAlertService.sendTest(
                id,
                request.recipient(),
                request.userAgent(),
                principal == null ? null : principal.getUserId()
        );
    }

    @GetMapping("/deliveries")
    public List<VersionAlertDelivery> deliveries(@RequestParam(defaultValue = "100") int limit) {
        return versionAlertService.deliveries(limit);
    }

    public record MatchRequest(String userAgent) {
    }

    public record TestRequest(String recipient, String userAgent) {
    }
}
