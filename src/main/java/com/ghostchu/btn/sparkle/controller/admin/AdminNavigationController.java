package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.NavigationItemInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.NavigationModels.NavigationItemView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.NavigationService;
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
@RequestMapping("/api/admin/navigation")
@RequiredArgsConstructor
public class AdminNavigationController {
    private final NavigationService navigationService;

    @GetMapping
    public List<NavigationItemView> list() {
        return navigationService.listAdmin();
    }

    @PostMapping
    public NavigationItemView create(
            @RequestBody NavigationItemInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return navigationService.create(
                input,
                principal == null ? null : principal.getUserId()
        );
    }

    @PutMapping("/{id}")
    public NavigationItemView update(
            @PathVariable String id,
            @RequestBody NavigationItemInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return navigationService.update(
                id,
                input,
                principal == null ? null : principal.getUserId()
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) throws BusinessException {
        navigationService.delete(id);
    }
}
