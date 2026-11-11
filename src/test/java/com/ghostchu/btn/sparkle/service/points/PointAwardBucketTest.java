package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointAwardBucketTest {
    @Test
    void mergesChangesFromTheSameRequest() {
        PointAwardBucket bucket = new PointAwardBucket(
                42,
                "BAN_HISTORY_SYNC",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        bucket.addContribution(PointCategory.BAN_HISTORY, 3, 1, 0);
        bucket.addContribution(PointCategory.BAN_HISTORY, 6, 2, 0);
        bucket.addContribution(PointCategory.NEW_TORRENT, 500, 1, 0);
        bucket.addClaim(
                "ACTIVE_APP_DAILY",
                "2026-11-12:7",
                PointCategory.ACTIVE_APP,
                5_000
        );
        bucket.addClaim(
                "ACTIVE_APP_DAILY",
                "2026-11-12:7",
                PointCategory.ACTIVE_APP,
                5_000
        );
        bucket.addRuleMatchedIps(List.of("203.0.113.10"));
        bucket.addSubmittedIps(List.of("203.0.113.10", "2001:db8::1"));

        assertEquals(9, bucket.getContributions()
                .get(PointCategory.BAN_HISTORY)
                .getPoints());
        assertEquals(3, bucket.getContributions()
                .get(PointCategory.BAN_HISTORY)
                .getItemCount());
        assertEquals(1, bucket.getClaims().size());
        assertEquals(1, bucket.getRuleMatchedIps().size());
        assertEquals(2, bucket.getSubmittedIps().size());
        assertTrue(bucket.hasWork());
    }
}
