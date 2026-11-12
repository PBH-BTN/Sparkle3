package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.GithubJoinResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.GithubStatusView;
import com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.GroupView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.github.CommunityGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ui/community-groups")
@RequiredArgsConstructor
public class CommunityGroupController {
    private final CommunityGroupService groupService;

    @GetMapping
    public List<GroupView> list(@AuthenticationPrincipal SparkleUserDetails principal) {
        return groupService.listEnabled(userId(principal));
    }

    @GetMapping("/{id}/github-status")
    public GithubStatusView githubStatus(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return groupService.status(userId(principal), id);
    }

    @PostMapping("/{id}/join")
    public GithubJoinResult join(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return groupService.join(userId(principal), id);
    }

    private long userId(SparkleUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
