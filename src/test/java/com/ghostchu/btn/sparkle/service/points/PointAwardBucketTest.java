package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        bucket.addContribution(PointCategory.RULE_HIT_IP, 10, 1, 0);
        bucket.addContribution(PointCategory.RULE_HIT_IP, 10, 1, 0);
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

        assertEquals(9, bucket.getContributions()
                .get(PointCategory.BAN_HISTORY)
                .getPoints());
        assertEquals(3, bucket.getContributions()
                .get(PointCategory.BAN_HISTORY)
                .getItemCount());
        assertEquals(20, bucket.getContributions()
                .get(PointCategory.RULE_HIT_IP)
                .getPoints());
        assertEquals(2, bucket.getContributions()
                .get(PointCategory.RULE_HIT_IP)
                .getItemCount());
        assertEquals(1, bucket.getClaims().size());
        assertTrue(bucket.hasWork());
    }
}
