package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AnnouncementService;
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
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminAnnouncementController {
    private final AnnouncementService announcementService;

    @GetMapping
    public List<AnnouncementView> list() {
        return announcementService.listAll();
    }

    @PostMapping
    public AnnouncementView create(
            @RequestBody AnnouncementInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return announcementService.create(
                input,
                principal == null ? null : principal.getUserId()
        );
    }

    @PutMapping("/{id}")
    public AnnouncementView update(
            @PathVariable long id,
            @RequestBody AnnouncementInput input,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return announcementService.update(
                id,
                input,
                principal == null ? null : principal.getUserId()
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) throws BusinessException {
        announcementService.delete(id);
    }
}
