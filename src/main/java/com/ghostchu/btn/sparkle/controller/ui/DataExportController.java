package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.AcceptedExport;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.ExportOptions;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.ExportRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.export.DataExportService;
import com.ghostchu.btn.sparkle.service.export.ExportDownloadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/ui/exports")
@RequiredArgsConstructor
public class DataExportController {
    private final DataExportService exports;
    private final ExportDownloadService downloads;

    @GetMapping("/options")
    public ExportOptions options(@AuthenticationPrincipal SparkleUserDetails principal,
                                 @RequestParam(defaultValue = "zh") String language) throws BusinessException {
        return exports.options(userId(principal), language);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcceptedExport enqueue(@AuthenticationPrincipal SparkleUserDetails principal,
                                  @RequestBody ExportRequest body, HttpServletRequest request) throws BusinessException {
        return new AcceptedExport(exports.enqueue(userId(principal), body, request.getRemoteAddr(),
                request.getHeader("User-Agent")));
    }

    @GetMapping("/{id}/download")
    public void download(@AuthenticationPrincipal SparkleUserDetails principal,
                         @PathVariable UUID id, @RequestParam(required = false) String token,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        try {
            downloads.download(exports.authorizeDownload(userId(principal), id, token), request, response);
        } catch (BusinessException exception) {
            response.setStatus(exception.getStatusCode().value());
            response.setContentType("text/plain;charset=UTF-8");
            if (!"HEAD".equals(request.getMethod())) {
                response.getWriter().write(downloadError(exception, "zh".equals(request.getLocale().getLanguage())));
            }
        }
    }

    private static String downloadError(BusinessException exception, boolean chinese) {
        return switch (exception.getStatusCode().value()) {
            case 401 -> chinese ? "请登录后再下载。" : "Sign in to download this file.";
            case 403 -> chinese ? "只有支持者可以下载导出文件。" : "Only supporters can download exports.";
            case 404 -> chinese ? "下载链接无效或已过期。" : "This download link is invalid or has expired.";
            case 429 -> chinese ? "已达到 32 个并发下载连接。请关闭部分连接后重试。"
                    : "You already have 32 download connections. Close some and try again.";
            default -> chinese ? "下载暂时不可用，请稍后重试。" : "The download is temporarily unavailable. Try again later.";
        };
    }

    private static long userId(SparkleUserDetails principal) throws BusinessException {
        if (principal == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return principal.getUserId();
    }
}
