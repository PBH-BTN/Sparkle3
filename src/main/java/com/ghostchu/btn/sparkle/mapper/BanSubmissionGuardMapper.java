package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.controller.admin.dto.BanSubmissionModels.Event;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface BanSubmissionGuardMapper {
    record Fingerprint(OffsetDateTime day, String hash) {}

    record Target(String hash, String ip, int port, String module, String rule,
                  OffsetDateTime latest, long replays) {}

    record BlockedTarget(String hash, long count) {}

    record EventQuery(Long appId, String kind, String status, OffsetDateTime since, OffsetDateTime until,
                      int threshold, boolean suppression, OffsetDateTime quietCutoff) {}

    List<String> insertFingerprints(@Param("rows") List<Fingerprint> rows);

    List<String> observe(@Param("appId") long appId, @Param("rows") List<Target> rows,
                         @Param("now") OffsetDateTime now, @Param("quietCutoff") OffsetDateTime quietCutoff,
                         @Param("previousWindow") OffsetDateTime previousWindow, @Param("windowMillis") long windowMillis,
                         @Param("threshold") int threshold, @Param("suppression") boolean suppression);

    void recordSuppressed(@Param("appId") long appId, @Param("rows") List<BlockedTarget> rows);

    long countEvents(@Param("query") EventQuery query);

    List<Event> listEvents(@Param("query") EventQuery query, @Param("offset") long offset, @Param("size") long size);

    List<String> dropExpiredFingerprints(@Param("cutoff") OffsetDateTime cutoff);

    int deleteExpiredTargets(@Param("stateCutoff") OffsetDateTime stateCutoff,
                             @Param("eventCutoff") OffsetDateTime eventCutoff, @Param("limit") int limit);
}
