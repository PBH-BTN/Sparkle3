package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Category;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.ExportRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Settings;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.DataExportJobMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataExportServiceTest {
    private final long uid = 10;
    private final DataExportJobMapper jobs = mock(DataExportJobMapper.class);
    private final DataExportSettings settings = mock(DataExportSettings.class);
    private final ExportQueries queries = mock(ExportQueries.class);
    private final IUserService users = mock(IUserService.class);
    private final DataExportService service = new DataExportService(jobs, settings, queries, users, mock(UserMessageService.class));

    @Test
    void downloadRequiresTheOwnerValidTokenUnexpiredFileAndCurrentSupporterRole() throws Exception {
        UUID id = UUID.randomUUID();
        String token = "a".repeat(43);
        var now = DataExportService.now();
        String digest = Hashing.sha256().hashString(token, StandardCharsets.US_ASCII).toString();
        var ready = new DataExportJob(id, uid, Category.BANS, "{}", "en", now, 10,
                100, 72, "READY", now.plusHours(1), 90L, digest);
        when(jobs.find(id, uid)).thenReturn(ready);
        when(users.getById(uid)).thenReturn(new User().setRoles(new String[]{"user", "supporter"}));
        when(users.getById(20L)).thenReturn(new User().setRoles(new String[]{"user", "supporter"}));

        assertEquals(ready, service.authorizeDownload(uid, id, token));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.authorizeDownload(20, id, token)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.authorizeDownload(uid, id, "b".repeat(43))).getStatusCode());
        when(jobs.find(id, uid)).thenReturn(new DataExportJob(id, uid, Category.BANS, "{}", "en", now,
                10, 100, 72, "READY", now.minusSeconds(1), 90L, digest));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.authorizeDownload(uid, id, token)).getStatusCode());
        when(users.getById(uid)).thenReturn(new User().setRoles(new String[]{"user", "admin"}));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> service.authorizeDownload(uid, id, token)).getStatusCode());
    }

    @Test
    void exhaustedQuotaAndFullPoolRejectBeforeCreatingAJob() throws Exception {
        when(users.getById(uid)).thenReturn(new User().setRoles(new String[]{"user", "supporter"}));
        when(settings.get()).thenReturn(new Settings(100, 10, 72, 300, 5, 20, "", ""));
        when(queries.validate(eq(Category.TRAFFIC), any(), any())).thenReturn("{}");
        var request = new ExportRequest(Category.TRAFFIC, Map.of(), "en");
        when(jobs.usedSlots(eq(uid), any())).thenReturn(5L);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(BusinessException.class,
                () -> service.enqueue(uid, request, "192.0.2.10", "test")).getStatusCode());

        when(jobs.usedSlots(eq(uid), any())).thenReturn(4L);
        when(jobs.reservedBytes()).thenReturn(11L * 1_048_576);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.enqueue(uid, request, "192.0.2.10", "test")).getStatusCode());
        verify(jobs, never()).enqueue(any(), any(), any(), any());
    }
}
