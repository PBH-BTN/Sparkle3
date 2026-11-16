package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.autoconfig.JacksonConfig;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.dto.BanHistoryQueryDto;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Category;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportQueriesTest {
    private final ExportQueries queries = new ExportQueries(null, null, null, null, JacksonConfig.objectMapper);

    @Test
    void storesTheEffectiveDefaultBanWindowWhenQueued() throws Exception {
        var now = OffsetDateTime.parse("2026-11-17T03:00:00Z");
        var filters = JacksonConfig.objectMapper.readValue(queries.validate(Category.BANS, Map.of(), now),
                BanHistoryQueryDto.class);

        assertEquals(now.minusDays(7), filters.getPopulateTimeStart());
        assertEquals(now, filters.getPopulateTimeEnd());
    }

    @Test
    void rejectsAnOversizedBanExportBeforeEnqueue() {
        var now = OffsetDateTime.parse("2026-05-01T00:00:00Z");
        var exception = assertThrows(ResponseStatusException.class, () -> queries.validate(Category.BANS,
                Map.of("populateTimeStart", "2026-01-31T00:00:00Z"), now));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("ban_history_range_too_long", exception.getReason());
    }
}
