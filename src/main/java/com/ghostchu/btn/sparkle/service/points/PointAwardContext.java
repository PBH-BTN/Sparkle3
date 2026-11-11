package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

@Component
@RequiredArgsConstructor
public class PointAwardContext {
    private final ThreadLocal<PointAwardBucket> current = new ThreadLocal<>();
    private final PointToggleService pointToggleService;

    public void begin(String eventType) {
        current.remove();
        final String resolvedEventType = eventType == null || eventType.isBlank()
                ? "UNKNOWN_EVENT"
                : eventType;
        current.set(new PointAwardBucket(
                -1,
                resolvedEventType,
                OffsetDateTime.now(ZoneOffset.UTC)
        ));
    }

    public PointAwardBucket finish() {
        PointAwardBucket bucket = current.get();
        current.remove();
        return bucket;
    }

    public void clear() {
        current.remove();
    }

    public void record(
            long userId,
            PointCategory category,
            long points,
            long itemCount,
            long rawValue
    ) {
        if (!pointToggleService.isEnabled(category)) {
            return;
        }
        PointAwardBucket bucket = ensureUser(userId);
        if (bucket != null) {
            bucket.addContribution(category, points, itemCount, rawValue);
        }
    }

    public void recordSimple(long userId, PointCategory category, long points) {
        record(userId, category, points, 1, 0);
    }

    public void claim(
            long userId,
            String claimType,
            String reference,
            PointCategory category,
            long points
    ) {
        if (!pointToggleService.isEnabled(category)) {
            return;
        }
        PointAwardBucket bucket = ensureUser(userId);
        if (bucket != null) {
            bucket.addClaim(claimType, reference, category, points);
        }
    }

    public void touchApp(long userId, long userappId) {
        PointAwardBucket bucket = ensureUser(userId);
        if (bucket != null) {
            bucket.addAppActivity(userappId);
        }
    }

    public void submitIps(long userId, Collection<String> ips) {
        if (!pointToggleService.isEnabled(PointCategory.NEW_IP)) {
            return;
        }
        PointAwardBucket bucket = ensureUser(userId);
        if (bucket != null) {
            bucket.addSubmittedIps(ips);
        }
    }

    public void submitRuleMatchedIps(long userId, Collection<String> ips) {
        if (!pointToggleService.isEnabled(PointCategory.RULE_HIT_IP)) {
            return;
        }
        PointAwardBucket bucket = ensureUser(userId);
        if (bucket != null) {
            bucket.addRuleMatchedIps(ips);
        }
    }

    public boolean hasWork() {
        PointAwardBucket bucket = current.get();
        return bucket != null && bucket.hasWork();
    }

    private PointAwardBucket ensureUser(long userId) {
        PointAwardBucket bucket = current.get();
        if (bucket == null) {
            return null;
        }
        if (bucket.getUserId() < 0) {
            current.set(new PointAwardBucket(
                    userId,
                    bucket.getEventType(),
                    bucket.getOccurredAt()
            ));
            return current.get();
        }
        if (bucket.getUserId() != userId) {
            throw new IllegalStateException("A points event cannot contain multiple users");
        }
        return bucket;
    }
}
