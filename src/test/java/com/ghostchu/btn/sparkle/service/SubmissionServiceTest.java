package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.btn.sparkle.mapper.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubmissionServiceTest {
    @Test
    void queriesAllTypesWithOwnerAndExactRollingWindow() {
        for (String type : new String[]{"bans", "clients", "swarm"}) {
            for (int days : new int[]{1, 3, 7}) {
                SubmissionMapper mapper = mock(SubmissionMapper.class);
                when(mapper.findApplicationIds(42L)).thenReturn(List.of(10L, 20L));
                new SubmissionService(mapper).query(42L, type, days, 2, 50);
                ArgumentCaptor<OffsetDateTime> start = ArgumentCaptor.forClass(OffsetDateTime.class);
                ArgumentCaptor<OffsetDateTime> end = ArgumentCaptor.forClass(OffsetDateTime.class);
                ArgumentCaptor<IPage> page = ArgumentCaptor.forClass(IPage.class);
                verify(mapper).query(page.capture(), eq(42L), eq(List.of(10L, 20L)), eq(type), start.capture(), end.capture());
                assertEquals(end.getValue().minusDays(days), start.getValue());
                assertEquals(2, page.getValue().getCurrent());
                assertEquals(50, page.getValue().getSize());
            }
        }
    }

    @Test
    void skipsRecordQueriesWhenUserHasNoApplications() {
        SubmissionMapper mapper = mock(SubmissionMapper.class);
        when(mapper.findApplicationIds(42L)).thenReturn(List.of());
        var result = new SubmissionService(mapper).query(42L, "bans", 3, 2, 50);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(2, result.getCurrent());
        assertEquals(50, result.getSize());
        verify(mapper).findApplicationIds(42L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void rejectsUnsupportedRangesTypesAndPaginationBeforeQuery() {
        SubmissionMapper mapper = mock(SubmissionMapper.class);
        SubmissionService service = new SubmissionService(mapper);
        assertThrows(ResponseStatusException.class, () -> service.query(1, "other", 3, 1, 20));
        assertThrows(ResponseStatusException.class, () -> service.query(1, "bans", 30, 1, 20));
        assertThrows(ResponseStatusException.class, () -> service.query(1, "bans", 3, 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.query(1, "bans", 3, 1, 101));
        assertThrows(ResponseStatusException.class, () -> service.query(1, "bans", 3, 1, 0));
        verifyNoInteractions(mapper);
    }
}
