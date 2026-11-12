package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.GroupInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.GroupView;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.SettingsUpdateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.TeamValidationRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.TeamValidationResult;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.github.CommunityGroupService;
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
@RequestMapping("/api/admin/community-groups")
@RequiredArgsConstructor
public class AdminCommunityGroupController {
    private final CommunityGroupService groupService;

    @GetMapping
    public SettingsView settings() {
        return groupService.settings();
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(
            @RequestBody SettingsUpdateRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        if (request == null) {
            throw new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "GitHub community settings are required");
        }
        groupService.updateSettings(
                request.personalAccessToken(),
                Boolean.TRUE.equals(request.clearToken()),
                request.apiBaseUrl(),
                principal == null ? null : principal.getUserId()
        );
        return groupService.settings();
    }

    @PostMapping
    public GroupView create(
            @RequestBody GroupInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return groupService.create(input, principal == null ? null : principal.getUserId());
    }

    @PutMapping("/{id}")
    public GroupView update(
            @PathVariable long id,
            @RequestBody GroupInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return groupService.update(
                id,
                input,
                principal == null ? null : principal.getUserId()
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) throws BusinessException {
        groupService.delete(id);
    }

    @PostMapping("/validate-teams")
    public TeamValidationResult validateTeams(
            @RequestBody TeamValidationRequest request
    ) throws BusinessException {
        if (request == null) {
            throw new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "GitHub team validation request is required");
        }
        return groupService.validateTeams(request.organization(), request.teamSlugs());
    }
}
