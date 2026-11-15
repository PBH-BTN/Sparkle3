package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.ContentModerationAdminModels.DictionaryUpdate;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.moderation.ContentModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content-moderation")
@RequiredArgsConstructor
public class AdminContentModerationController {
    private final ContentModerationService service;

    @GetMapping
    public ContentModerationService.Settings get() {
        return service.settings();
    }

    @PutMapping
    public ContentModerationService.Settings save(@RequestBody DictionaryUpdate request,
                                                  @AuthenticationPrincipal SparkleUserDetails principal)
            throws BusinessException {
        service.save(request.version(), request.enabled(), request.words(), principal.getUserId());
        return service.settings();
    }
}
