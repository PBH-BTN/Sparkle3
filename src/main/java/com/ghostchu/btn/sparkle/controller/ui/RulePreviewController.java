package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewResponse;
import com.ghostchu.btn.sparkle.service.RulePreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/rules")
@RequiredArgsConstructor
public class RulePreviewController {
    private final RulePreviewService rulePreviewService;

    @GetMapping("/preview")
    public RulePreviewResponse preview() {
        return rulePreviewService.preview();
    }
}
