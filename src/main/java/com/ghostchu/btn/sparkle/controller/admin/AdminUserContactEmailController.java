package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.UserContactEmailAuditView;
import com.ghostchu.btn.sparkle.service.AdminUserContactEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/user-contact-emails")
@RequiredArgsConstructor
public class AdminUserContactEmailController {
    private final AdminUserContactEmailService service;

    @GetMapping
    public PageResult<UserContactEmailAuditView> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword
    ) {
        return service.list(page, size, keyword);
    }
}
