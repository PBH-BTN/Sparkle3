package com.ghostchu.btn.sparkle.util;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcTimeTest {
    @Test
    void convertsJdbcTimestampAsUtcInstant() {
        Instant instant = Instant.parse("2026-11-13T12:00:00Z");

        assertEquals(
                OffsetDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneOffset.UTC),
                JdbcTime.toOffsetDateTime(Timestamp.from(instant))
        );
    }

    @Test
    void normalizesOffsetDateTimeToUtc() {
        OffsetDateTime value = OffsetDateTime.of(
                2026, 11, 13, 20, 0, 0, 0, ZoneOffset.ofHours(8)
        );

        assertEquals(
                OffsetDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneOffset.UTC),
                JdbcTime.toOffsetDateTime(value)
        );
    }

    @Test
    void convertsInstant() {
        Instant instant = Instant.parse("2026-11-13T12:00:00Z");

        assertEquals(
                OffsetDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneOffset.UTC),
                JdbcTime.toOffsetDateTime(instant)
        );
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcTime.toOffsetDateTime("2026-11-13T12:00:00Z")
        );
    }
}
