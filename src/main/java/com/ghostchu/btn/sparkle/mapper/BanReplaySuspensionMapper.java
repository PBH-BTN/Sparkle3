package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface BanReplaySuspensionMapper {
    enum MailStatus { PENDING, SENT, SKIPPED, FAILED }

    record Notice(long id, long applicationId, String appId, String email, String nickname,
                  OffsetDateTime suspendedAt, long replayBatches, long replayLogs,
                  long batchLimit, long logLimit, int attempt) {}

    int suspendAndQueue(@Param("since") LocalDate since, @Param("checkBatches") boolean checkBatches,
                        @Param("batchLimit") long batchLimit, @Param("checkLogs") boolean checkLogs,
                        @Param("logLimit") long logLimit, @Param("reason") String reason,
                        @Param("limit") int limit);

    Notice claimNotice();

    int finishNotice(@Param("id") long id, @Param("attempt") int attempt, @Param("status") MailStatus status);
}
