package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.InboxPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.UnreadMessageCount;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ui/messages")
@RequiredArgsConstructor
public class InboxApiController {
    private final UserMessageService messageService;

    @GetMapping
    public InboxPage list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return messageService.inbox(currentUserId(principal), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    public UnreadMessageCount unreadCount(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return new UnreadMessageCount(messageService.unreadCount(currentUserId(principal)));
    }

    @PostMapping("/{id}/read")
    public void markRead(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        messageService.markRead(currentUserId(principal), id);
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal SparkleUserDetails principal) {
        messageService.markAllRead(currentUserId(principal));
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable long id,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        messageService.delete(currentUserId(principal), id);
    }

    private long currentUserId(SparkleUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
