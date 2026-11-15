package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import com.ghostchu.btn.sparkle.util.SuspensionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSuspensionServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final UserappMapper apps = mock(UserappMapper.class);
    private final AdminSuspensionService service = new AdminSuspensionService(users, apps);

    @Test
    void rejectsBlankReasonsOnEverySuspensionWritePath() {
        var user = new User().setId(1L);
        var app = new Userapp().setId(2L);
        for (String reason : new String[]{null, "", " \t\n", "\u00a0\u3000"}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.setUserSuspended(user, true, reason, null)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.setApplicationSuspended(app, true, reason, null)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.updateApplication(app, true, reason, null)).getStatusCode());
        }
        verifyNoInteractions(users, apps);
    }

    @Test
    void rejectsPastExpiryAndOversizedReasons() {
        var user = new User().setId(1L);
        var app = new Userapp().setId(2L);
        var past = OffsetDateTime.now().minusDays(1);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.setUserSuspended(user, true, "Reason", past)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.setApplicationSuspended(app, true, "x".repeat(501), null)).getStatusCode());
        verifyNoInteractions(users, apps);
    }

    @Test
    void expiresAtTheSameInstantAcrossOffsets() {
        var end = OffsetDateTime.parse("2026-11-16T20:00:00+08:00");
        var start = end.minusDays(1);
        assertTrue(SuspensionStatus.isActive(start, null, end));
        assertTrue(SuspensionStatus.isActive(start, end, end.minusNanos(1)));
        assertFalse(SuspensionStatus.isActive(start, end, OffsetDateTime.parse("2026-11-16T12:00:00Z")));
        assertFalse(SuspensionStatus.isActive(null, end, end.minusDays(1)));
    }
}
