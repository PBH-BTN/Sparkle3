package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.AiModerationAdminView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.AiModerationSettingsUpdate;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.AiModerationSettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.IpQueryCommentSettingsUpdate;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.IpQueryCommentSettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.ModerationJobView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.ModerationMetricsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.ModerationTestRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.ModerationTestResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.AiModerationAdminModels.RequeueResult;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import com.ghostchu.btn.sparkle.service.ai.AiModerationSettings;
import com.ghostchu.btn.sparkle.service.ai.AiModerationSettingsService;
import com.ghostchu.btn.sparkle.service.ai.OpenAiModerationClient;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQuerySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/ai-moderation")
@RequiredArgsConstructor
public class AdminAiModerationController {
    private final AiModerationSettingsService settingsService;
    private final AiModerationService moderationService;
    private final OpenAiModerationClient moderationClient;
    private final IpQuerySettingsService querySettingsService;
    private final IpQueryCommentService commentService;

    @GetMapping
    public AiModerationAdminView get() {
        return view();
    }

    @PutMapping("/settings")
    public AiModerationAdminView updateSettings(
            @RequestBody AiModerationSettingsUpdate request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "AI moderation settings are required");
        }
        settingsService.update(
                new AiModerationSettingsService.UpdateRequest(
                        request.enabled(),
                        request.baseUrl(),
                        blankToNull(request.apiKey()),
                        request.clearApiKey(),
                        request.model(),
                        request.rubric(),
                        request.timeoutSeconds(),
                        request.maxAttempts(),
                        request.moderateAvatars()
                ),
                principal == null ? null : principal.getUserId()
        );
        return view();
    }

    @PutMapping("/comments")
    public AiModerationAdminView updateComments(
            @RequestBody IpQueryCommentSettingsUpdate request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "IP query comment settings are required");
        }
        querySettingsService.update(
                request.commentsEnabled(),
                request.commentsVisible(),
                request.maxLength(),
                request.hourlyLimit(),
                principal == null ? null : principal.getUserId()
        );
        return view();
    }

    @PostMapping("/test")
    public ModerationTestResult test(@RequestBody ModerationTestRequest request)
            throws BusinessException {
        if (request == null
                || request.content() == null
                || request.content().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Test content is required");
        }
        OpenAiModerationClient.Verdict verdict;
        try {
            verdict = moderationClient.review(
                    settingsService.get(),
                    "COMMENT",
                    "管理员配置测试",
                    request.content().trim(),
                    null
            );
        } catch (IllegalStateException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_GATEWAY,
                    exception.getMessage()
            );
        }
        return new ModerationTestResult(
                verdict.approved(),
                verdict.reason(),
                verdict.categories(),
                verdict.confidence()
        );
    }

    @PostMapping("/comments/requeue")
    public RequeueResult requeueAll() throws BusinessException {
        return new RequeueResult(commentService.requeueAll());
    }

    @PostMapping("/jobs/retry")
    public RequeueResult retry(
            @RequestParam(required = false) String contentType
    ) {
        return new RequeueResult(moderationService.retryFailed(contentType));
    }

    private AiModerationAdminView view() {
        AiModerationSettings settings = settingsService.get();
        var commentSettings = querySettingsService.get();
        var metrics = moderationService.metrics();
        return new AiModerationAdminView(
                new AiModerationSettingsView(
                        settings.enabled(),
                        settings.baseUrl(),
                        settings.apiKeyConfigured(),
                        settings.model(),
                        settings.rubric(),
                        settings.timeoutSeconds(),
                        settings.maxAttempts(),
                        settings.moderateAvatars()
                ),
                new IpQueryCommentSettingsView(
                        commentSettings.commentsEnabled(),
                        commentSettings.commentsVisible(),
                        commentSettings.maxLength(),
                        commentSettings.hourlyLimit()
                ),
                new ModerationMetricsView(
                        metrics.pending(),
                        metrics.running(),
                        metrics.approved(),
                        metrics.rejected(),
                        metrics.failed()
                ),
                moderationService.recentJobs(25).stream()
                        .map(job -> new ModerationJobView(
                                job.getId(),
                                job.getContentType(),
                                job.getContentRefId(),
                                job.getContentVersion(),
                                job.getSubjectUserId(),
                                job.getStatus(),
                                job.getAttemptCount() == null ? 0 : job.getAttemptCount(),
                                job.getMaxAttempts() == null ? 0 : job.getMaxAttempts(),
                                job.getProviderModel(),
                                job.getRejectionReason(),
                                job.getLastError(),
                                job.getCreatedAt(),
                                job.getCompletedAt()
                        ))
                        .toList(),
                OffsetDateTime.now()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
