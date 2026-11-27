package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.BtnConfigView;
import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.UpdateBtnConfigRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.btn.AdminBtnConfigService;
import com.ghostchu.btn.sparkle.service.btn.AdminBtnConfigGroups;
import com.ghostchu.btn.sparkle.service.btn.BtnConfigCatalog;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/editors")
    public Map<String, String> editors() {
        return BtnConfigCatalog.definitions().stream()
                .collect(Collectors.toMap(
                        BtnConfigCatalog.Definition::key,
                        definition -> "/admin/btn/" + AdminBtnConfigGroups.groupOf(definition)));
    }

    @GetMapping("/groups/{group}")
    public BtnConfigView getGroup(@PathVariable String group) throws BusinessException {
        return configService.getGroup(group);
    }

    @PutMapping("/groups/{group}")
    public BtnConfigView updateGroup(@PathVariable String group, @RequestBody UpdateBtnConfigRequest request,
                                    @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        return configService.updateGroup(group, request.values(), principal == null ? null : principal.getUserId());
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
