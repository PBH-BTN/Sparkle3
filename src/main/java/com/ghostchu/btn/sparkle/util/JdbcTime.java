package com.ghostchu.btn.sparkle.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * MyBatis 把查询结果映射成 {@code Map} 时不会使用 {@link OffsetDateTime} 类型处理器，
 * PostgreSQL 的 {@code timestamptz} 列会以 {@link Timestamp} 返回。读取这类原始结果时
 * 统一通过这里转换，避免直接强转导致 {@link ClassCastException}。
 */
public final class JdbcTime {
    private JdbcTime() {
    }

    public static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        throw new IllegalArgumentException(
                "Unsupported JDBC timestamp value: " + value
        );
    }
}
