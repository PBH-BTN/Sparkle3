package com.ghostchu.btn.sparkle.controller.ui;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.SubmissionModels.SubmissionRecord;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ui/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionService service;

    @GetMapping
    public IPage<SubmissionRecord> query(@AuthenticationPrincipal SparkleUserDetails principal,
                                        @RequestParam(defaultValue = "bans") String type,
                                        @RequestParam(defaultValue = "3") int days,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return service.query(principal.getUserId(), type, days, page, size);
    }
}
