package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentCreateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentDetail;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentRow;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentUpdateRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ip-query-comments")
@RequiredArgsConstructor
public class AdminIpQueryCommentController {
    private final IpQueryCommentAdminService commentService;

    @GetMapping
    public PageResult<AdminCommentRow> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String scope,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) throws BusinessException {
        return commentService.list(
                page,
                size,
                keyword,
                scope,
                status,
                includeDeleted
        );
    }

    @GetMapping("/{id}")
    public AdminCommentDetail detail(@PathVariable long id)
            throws BusinessException {
        return commentService.detail(id);
    }

    @PostMapping
    public AdminCommentDetail create(
            @RequestBody AdminCommentCreateRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal,
            HttpServletRequest httpRequest
    ) throws BusinessException {
        return commentService.create(
                principal.getUserId(),
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PutMapping("/{id}")
    public AdminCommentDetail update(
            @PathVariable long id,
            @RequestBody AdminCommentUpdateRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal,
            HttpServletRequest httpRequest
    ) throws BusinessException {
        return commentService.update(
                principal.getUserId(),
                id,
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
    }

    @DeleteMapping("/{id}")
    public AdminCommentDetail delete(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return commentService.delete(principal.getUserId(), id);
    }

    @PostMapping("/{id}/restore")
    public AdminCommentDetail restore(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return commentService.restore(principal.getUserId(), id);
    }

    @PostMapping("/{id}/reaudit")
    public AdminCommentDetail reaudit(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return commentService.reaudit(principal.getUserId(), id);
    }
}
