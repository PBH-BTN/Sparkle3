package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.RunResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetRunResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetView;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.UpdateSettingsRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.github.AdminGithubSyncService;
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

@RestController
@RequestMapping("/api/admin/github-sync")
@RequiredArgsConstructor
public class AdminGithubSyncController {
    private final AdminGithubSyncService service;

    @GetMapping
    public SettingsView get() throws BusinessException {
        return service.get();
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(
            @RequestBody UpdateSettingsRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return service.updateSettings(
                request,
                principal == null ? null : principal.getUserId()
        );
    }

    @PostMapping("/targets")
    public TargetView create(
            @RequestBody TargetInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return service.create(input, principal == null ? null : principal.getUserId());
    }

    @PutMapping("/targets/{id}")
    public TargetView update(
            @PathVariable long id,
            @RequestBody TargetInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return service.update(id, input, principal == null ? null : principal.getUserId());
    }

    @DeleteMapping("/targets/{id}")
    public void delete(@PathVariable long id) throws BusinessException {
        service.delete(id);
    }

    @PostMapping("/run")
    public RunResult runAll() {
        return service.runAll();
    }

    @PostMapping("/targets/{id}/run")
    public TargetRunResult runTarget(@PathVariable long id) throws BusinessException {
        return service.runTarget(id);
    }
}
