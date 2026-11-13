package com.ghostchu.btn.sparkle.service.points;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public final class PointAwardBucket {
    private final long userId;
    private final UUID eventId;
    private final OffsetDateTime occurredAt;
    private final String eventType;
    private final Map<PointCategory, Contribution> contributions =
            new EnumMap<>(PointCategory.class);
    private final List<Claim> claims = new ArrayList<>();
    private final Map<Long, Long> appActivities = new LinkedHashMap<>();

    public PointAwardBucket(
            long userId,
            String eventType,
            OffsetDateTime occurredAt
    ) {
        this.userId = userId;
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public synchronized void addContribution(
            PointCategory category,
            long points,
            long itemCount,
            long rawValue
    ) {
        if (points <= 0) {
            return;
        }
        Contribution contribution = contributions.computeIfAbsent(
                category,
                ignored -> new Contribution()
        );
        contribution.points = safeAdd(contribution.points, points);
        contribution.itemCount = safeAdd(contribution.itemCount, itemCount);
        contribution.rawValue = safeAdd(contribution.rawValue, rawValue);
    }

    public synchronized void addClaim(
            String claimType,
            String reference,
            PointCategory category,
            long points
    ) {
        if (points <= 0) {
            return;
        }
        if (claims.stream().anyMatch(claim ->
                claim.claimType.equals(claimType)
                        && claim.reference.equals(reference))) {
            return;
        }
        claims.add(new Claim(claimType, reference, category, points));
    }

    public synchronized void addAppActivity(long userappId) {
        appActivities.put(userappId, userId);
    }

    public synchronized boolean hasWork() {
        return !contributions.isEmpty()
                || !claims.isEmpty()
                || !appActivities.isEmpty();
    }

    private static long safeAdd(long first, long second) {
        if (second <= 0) {
            return first;
        }
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    @Getter
    public static final class Contribution {
        private long points;
        private long itemCount;
        private long rawValue;
    }

    public record Claim(
            String claimType,
            String reference,
            PointCategory category,
            long points
    ) {
    }
}
