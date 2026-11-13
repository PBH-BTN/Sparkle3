package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MessageModels {
    private MessageModels() {
    }

    public record NotificationPreferenceView(
            String category,
            boolean inboxEnabled,
            boolean emailEnabled,
            boolean locked
    ) {
    }

    public record NotificationPreferencesResponse(
            List<NotificationPreferenceView> preferences
    ) {
    }

    public record NotificationPreferenceUpdate(
            String category,
            Boolean inboxEnabled,
            Boolean emailEnabled
    ) {
    }

    public record NotificationPreferencesUpdate(
            List<NotificationPreferenceUpdate> preferences
    ) {
    }

    public record InboxMessageView(
            long id,
            String senderDisplayName,
            String senderKind,
            String category,
            String subject,
            String content,
            OffsetDateTime createdAt,
            OffsetDateTime readAt,
            boolean read,
            boolean broadcast,
            boolean emailRequested,
            boolean emailForced,
            String emailStatus
    ) {
    }

    public record InboxPage(
            List<InboxMessageView> records,
            long current,
            long size,
            long total,
            long pages
    ) {
    }

    public record UnreadMessageCount(int count) {
    }

    public record SendMessageRequest(
            String subject,
            String content
    ) {
    }

    public record SendMessageResult(
            boolean sent,
            int remainingToday
    ) {
    }

    public record StaffMessageRequest(
            String subject,
            String content,
            String category,
            Long recipientId,
            boolean systemIdentity,
            boolean forceEmail
    ) {
    }

    public record StaffMessageResult(
            int recipients,
            UUID dispatchId,
            int remainingToday
    ) {
    }
}
