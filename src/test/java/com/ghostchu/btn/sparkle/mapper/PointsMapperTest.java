package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointsMapperTest {
    @Test
    void castsOnlineGapToPostgresInteger() throws Exception {
        var method = PointsMapper.class.getMethod(
                "touchAppActivity",
                long.class,
                long.class,
                OffsetDateTime.class,
                int.class
        );
        assertEquals(int.class, method.getParameterTypes()[3]);

        Configuration configuration = new Configuration();
        configuration.addMapper(PointsMapper.class);
        var statement = configuration.getMappedStatement(
                PointsMapper.class.getName() + ".touchAppActivity"
        );
        var boundSql = statement.getBoundSql(Map.of(
                "userappId", 11L,
                "userId", 22L,
                "occurredAt", OffsetDateTime.of(
                        2026, 11, 13, 0, 0, 0, 0, ZoneOffset.UTC
                ),
                "onlineGapMinutes", 15
        ));
        String sql = boundSql.getSql().replaceAll("\\s+", " ");

        assertEquals("onlineGapMinutes", boundSql.getParameterMappings().get(3).getProperty());
        assertTrue(sql.contains("CAST(? AS INTEGER)"));
    }
}
