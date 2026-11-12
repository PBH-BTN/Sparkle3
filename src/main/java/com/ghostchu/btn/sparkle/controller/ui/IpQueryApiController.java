package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentView;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CreateCommentRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.LookupResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.UpdateCommentRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryLookupService;
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
@RequestMapping("/api/ui/ip-query")
@RequiredArgsConstructor
public class IpQueryApiController {
    private final IpQueryLookupService lookupService;
    private final IpQueryCommentService commentService;

    @GetMapping("/lookup")
    public LookupResponse lookup(@RequestParam("query") String query)
            throws BusinessException {
        return lookupService.lookup(query);
    }

    @GetMapping("/comments")
    public CommentPage comments(
            @RequestParam("scope") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        return commentService.list(
                principal.getUserId(),
                scope,
                page,
                size
        );
    }

    @PostMapping("/comments")
    public CommentView create(
            @RequestBody CreateCommentRequest request,
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

    @PutMapping("/comments/{id}")
    public CommentView update(
            @PathVariable long id,
            @RequestBody UpdateCommentRequest request,
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

    @DeleteMapping("/comments/{id}")
    public void delete(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        commentService.delete(principal.getUserId(), id);
    }
}
