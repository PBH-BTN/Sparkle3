package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AnalyseModuleModels.ModuleView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnalyseModuleModels.RunModuleResponse;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnalyseModuleModels.UpdateModuleRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.service.AnalyseModuleScheduler;
import com.ghostchu.btn.sparkle.service.GeoIpCityBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/modules")
@RequiredArgsConstructor
public class AdminAnalyseModuleController {
    private final AnalyseModuleConfigService moduleConfigService;
    private final AnalyseModuleScheduler moduleScheduler;
    private final GeoIpCityBlacklistService geoIpCityBlacklistService;

    @GetMapping
    public List<ModuleView> list() {
        return moduleConfigService.listModules();
    }

    @PutMapping("/{id}")
    public ModuleView update(
            @PathVariable String id,
            @RequestBody UpdateModuleRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        ModuleView updated = moduleConfigService.update(
                id,
                request.values() == null ? java.util.Map.of() : request.values(),
                principal == null ? null : principal.getUserId()
        );
        moduleScheduler.refreshSchedules();
        if (GeoIpCityBlacklistService.MODULE_ID.equals(id) && updated.enabled()) {
            geoIpCityBlacklistService.requestRefresh(
                    principal == null ? null : principal.getUserId()
            );
        }
        return updated;
    }

    @PostMapping("/{id}/run")
    public RunModuleResponse run(@PathVariable String id) throws BusinessException {
        moduleScheduler.triggerNow(id);
        return new RunModuleResponse(id, true);
    }
}
