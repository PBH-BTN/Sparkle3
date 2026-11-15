package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.BanSubmissionModels.Event;
import com.ghostchu.btn.sparkle.service.BanSubmissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/ban-submissions")
@RequiredArgsConstructor
public class AdminBanSubmissionController {
    private final BanSubmissionGuard guard;

    @GetMapping
    public PageResult<Event> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OffsetDateTime since,
            @RequestParam(required = false) OffsetDateTime until
    ) {
        return guard.events(page, size, appId, kind, status, since, until);
    }
}
