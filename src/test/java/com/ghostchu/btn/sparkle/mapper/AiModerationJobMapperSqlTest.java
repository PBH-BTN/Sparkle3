package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModerationJobMapperSqlTest {
    @Test
    void completingAJobCastsResultJsonToJsonb() throws Exception {
        String sql = updateSql(
                "completeRunningJob",
                long.class,
                String.class,
                String.class,
                String.class,
                OffsetDateTime.class,
                OffsetDateTime.class
        );

        assertTrue(
                sql.contains("result_json = CAST(#{resultJson} AS jsonb)"),
                "result_json is jsonb; the update must cast the string parameter"
        );
        assertTrue(sql.contains("status = 'RUNNING'"));
    }

    @Test
    void retryingAFailedJobResetsItsAttempts() throws Exception {
        String sql = updateSql(
                "requeueFailedJob",
                long.class,
                int.class
        );

        assertTrue(sql.contains("status = 'PENDING'"));
        assertTrue(sql.contains("attempt_count = 0"));
        assertTrue(sql.contains("status = 'FAILED'"));
    }

    private String updateSql(String name, Class<?>... parameterTypes)
            throws Exception {
        Method method = AiModerationJobMapper.class.getMethod(name, parameterTypes);
        Update update = method.getAnnotation(Update.class);
        assertNotNull(update, name + " must be an annotated update statement");
        return String.join("\n", update.value());
    }
}
