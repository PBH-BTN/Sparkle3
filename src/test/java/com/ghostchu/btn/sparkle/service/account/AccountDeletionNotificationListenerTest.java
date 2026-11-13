package com.ghostchu.btn.sparkle.service.account;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountDeletionNotificationListenerTest {
    private static final OffsetDateTime REQUESTED_AT =
            OffsetDateTime.of(2026, 11, 13, 10, 0, 0, 0, ZoneOffset.ofHours(8));
    private static final OffsetDateTime COOLING_ENDS_AT = REQUESTED_AT.plusDays(7);

    @Test
    void startedNotificationExplainsCoolingAndCancellationRules() {
        var event = event(
                AccountDeletionNotificationEvent.Kind.STARTED,
                null,
                null
        );

        String body = AccountDeletionNotificationListener.body(event);

        assertTrue(body.contains("7 天冷静期"));
        assertTrue(body.contains("登录尝试或用户应用活动"));
        assertTrue(body.contains("账号个人标识将匿名化"));
        assertTrue(body.contains("2026-11-20"));
        assertEquals(
                "Sparkle BTN 账号注销申请已开始",
                AccountDeletionNotificationListener.subject(event.kind())
        );
    }

    @Test
    void cancelledNotificationContainsCancellationReason() {
        var event = event(
                AccountDeletionNotificationEvent.Kind.CANCELLED,
                null,
                "检测到用户应用 #91 活动"
        );

        String body = AccountDeletionNotificationListener.body(event);

        assertTrue(body.contains("检测到用户应用 #91 活动"));
        assertTrue(body.contains("账号仍可正常使用"));
        assertEquals(
                "Sparkle BTN 账号注销申请已取消",
                AccountDeletionNotificationListener.subject(event.kind())
        );
    }

    @Test
    void completedNotificationDescribesAnonymization() {
        var event = event(
                AccountDeletionNotificationEvent.Kind.COMPLETED,
                COOLING_ENDS_AT,
                null
        );

        String body = AccountDeletionNotificationListener.body(event);

        assertTrue(body.contains("注销已完成"));
        assertTrue(body.contains("所有用户应用均已停用"));
        assertTrue(body.contains("GitHub 绑定信息"));
        assertEquals(
                "Sparkle BTN 账号注销已完成",
                AccountDeletionNotificationListener.subject(event.kind())
        );
    }

    private AccountDeletionNotificationEvent event(
            AccountDeletionNotificationEvent.Kind kind,
            OffsetDateTime completedAt,
            String cancellationDetail
    ) {
        return new AccountDeletionNotificationEvent(
                42L,
                "user@example.com",
                "Test User",
                kind,
                REQUESTED_AT,
                COOLING_ENDS_AT,
                completedAt,
                cancellationDetail
        );
    }
}
