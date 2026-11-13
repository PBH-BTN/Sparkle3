package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

public interface AiModerationJobMapper extends BaseMapper<AiModerationJob> {
    @Select("""
            SELECT *
            FROM ai_moderation_jobs
            WHERE status = 'PENDING'
              AND next_attempt_at <= now()
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """)
    AiModerationJob selectNextForUpdate();

    @Update("""
            UPDATE ai_moderation_jobs
            SET status = 'PENDING',
                locked_by = NULL,
                locked_at = NULL,
                next_attempt_at = now(),
                updated_at = now(),
                last_error = COALESCE(last_error, 'Worker stopped before the moderation request completed')
            WHERE status = 'RUNNING'
              AND locked_at < #{staleBefore}
            """)
    int recoverStaleJobs(@Param("staleBefore") OffsetDateTime staleBefore);

    @Update("""
            WITH updated AS (
                UPDATE ip_query_comments
                SET moderation_status = 'PENDING',
                    moderation_reason = NULL,
                    review_generation = review_generation + 1,
                    updated_at = now()
                WHERE deleted_at IS NULL
                RETURNING id, review_generation, author_user_id, content
            )
            INSERT INTO ai_moderation_jobs (
                content_type, content_ref_id, content_version, subject_user_id,
                text_content, status, max_attempts, next_attempt_at
            )
            SELECT
                'COMMENT', id, review_generation, author_user_id,
                content, 'PENDING', #{maxAttempts}, now()
            FROM updated
            """)
    int requeueAllComments(@Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE ai_moderation_jobs
            SET status = 'PENDING',
                attempt_count = 0,
                next_attempt_at = now(),
                last_error = NULL,
                locked_by = NULL,
                locked_at = NULL,
                updated_at = now()
            WHERE (
                    status = 'FAILED'
                    OR (status = 'RUNNING'
                        AND locked_at < now() - INTERVAL '10 minutes')
                  )
              AND (CAST(#{contentType} AS VARCHAR) IS NULL
                   OR content_type = CAST(#{contentType} AS VARCHAR))
            """)
    int retryFailedJobs(@Param("contentType") String contentType);

    @Update("""
            UPDATE ai_moderation_jobs
            SET status = #{status},
                result_json = CAST(#{resultJson} AS jsonb),
                rejection_reason = #{rejectionReason},
                completed_at = #{completedAt},
                locked_by = NULL,
                locked_at = NULL,
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND status = 'RUNNING'
            """)
    int completeRunningJob(
            @Param("id") long id,
            @Param("status") String status,
            @Param("resultJson") String resultJson,
            @Param("rejectionReason") String rejectionReason,
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Update("""
            UPDATE ai_moderation_jobs
            SET status = 'PENDING',
                attempt_count = 0,
                max_attempts = #{maxAttempts},
                next_attempt_at = now(),
                last_error = NULL,
                locked_by = NULL,
                locked_at = NULL,
                completed_at = NULL,
                updated_at = now()
            WHERE id = #{id}
              AND status = 'FAILED'
            """)
    int requeueFailedJob(
            @Param("id") long id,
            @Param("maxAttempts") int maxAttempts
    );
}
