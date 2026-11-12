package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.StaffMessageRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.StaffMessageResult;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.AdminPolicy;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
public class AdminMessageController {
    private final UserMessageService messageService;

    @PostMapping
    public StaffMessageResult send(
            @RequestBody StaffMessageRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal,
            HttpServletRequest servletRequest
    ) throws BusinessException {
        if (principal == null
                || principal.getUser() == null
                || !AdminPolicy.hasModeratorAccess(principal.getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Administrator or moderator access required");
        }
        return messageService.sendAsStaff(
                principal.getUserId(),
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
    }
}
