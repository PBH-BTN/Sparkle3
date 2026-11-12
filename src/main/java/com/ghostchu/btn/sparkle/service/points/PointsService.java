package com.ghostchu.btn.sparkle.service.points;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PointAwardContext context;
    private final PointToggleService pointToggleService;
    private final PointRulesService pointRulesService;
    private final BtnRuntimeConfigService btnConfigService;

    public LocalDate currentPointDay() {
        return LocalDate.now(ZoneId.of(btnConfigService.text("points", "timeZone")));
    }

    public void recordAppActivity(long userId, long userappId) {
        if (pointToggleService.isEnabled(PointCategory.APP_ONLINE)) {
            context.touchApp(userId, userappId);
        }
        if (pointToggleService.isEnabled(PointCategory.ACTIVE_APP)) {
            context.claim(
                    userId,
                    "ACTIVE_APP_DAILY",
                    currentPointDay() + ":" + userappId,
                    PointCategory.ACTIVE_APP,
                    pointRulesService.points(PointCategory.ACTIVE_APP)
            );
        }
    }

    @Transactional
    public void awardDailyLogin(long userId) {
        if (!pointToggleService.isEnabled(PointCategory.LOGIN)) {
            return;
        }
        PointAwardBucket bucket = new PointAwardBucket(
                userId,
                "DAILY_LOGIN",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        bucket.addClaim(
                "DAILY_LOGIN",
                currentPointDay().toString(),
                PointCategory.LOGIN,
                pointRulesService.points(PointCategory.LOGIN)
        );
        commit(bucket);
    }

    @Transactional
    public void commit(PointAwardBucket bucket) {
        commitInternal(bucket);
    }

    @Transactional
    public void commitAll(List<PointAwardBucket> buckets) {
        for (PointAwardBucket bucket : buckets) {
            commitInternal(bucket);
        }
    }

    private void commitInternal(PointAwardBucket bucket) {
        if (bucket == null || !bucket.hasWork()) {
            return;
        }

        OffsetDateTime occurredAt = bucket.getOccurredAt();
        LocalDate pointDay = occurredAt.atZoneSameInstant(
                ZoneId.of(btnConfigService.text("points", "timeZone"))
        ).toLocalDate();

        for (Long userappId : bucket.getAppActivities().keySet()) {
            long onlineHours = jdbcTemplate.queryForObject(
                    """
                    SELECT public.sparkle_touch_point_app_activity(?, ?, ?, ?)
                    """,
                    Long.class,
                    userappId,
                    bucket.getUserId(),
                    occurredAt,
                    pointRulesService.onlineGapMinutes()
            );
            if (onlineHours > 0) {
                bucket.addContribution(
                        PointCategory.APP_ONLINE,
                        safeMultiply(
                                onlineHours,
                                pointRulesService.points(PointCategory.APP_ONLINE)
                        ),
                        onlineHours,
                        onlineHours
                );
            }
        }

        if (!bucket.getRuleMatchedIps().isEmpty()
                || !bucket.getSubmittedIps().isEmpty()) {
            SubmittedIpResult ipResult = markSubmittedIps(
                    bucket.getUserId(),
                    pointDay,
                    List.copyOf(bucket.getRuleMatchedIps()),
                    List.copyOf(bucket.getSubmittedIps())
            );
            if (ipResult.dailyUnique() > 0) {
                bucket.addContribution(
                        PointCategory.RULE_HIT_IP,
                        safeMultiply(
                                ipResult.dailyUnique(),
                                pointRulesService.points(PointCategory.RULE_HIT_IP)
                        ),
                        ipResult.dailyUnique(),
                        0
                );
            }
            if (ipResult.newUnique() > 0) {
                bucket.addContribution(
                        PointCategory.NEW_IP,
                        safeMultiply(
                                ipResult.newUnique(),
                                pointRulesService.points(PointCategory.NEW_IP)
                        ),
                        ipResult.newUnique(),
                        0
                );
            }
        }

        if (!bucket.getClaims().isEmpty()) {
            for (ClaimResult claim : claimAwards(bucket.getUserId(), bucket.getClaims())) {
                PointCategory category = parseCategory(claim.category());
                if (category != null) {
                    bucket.addContribution(category, claim.points(), 1, 0);
                }
            }
        }

        if (bucket.getContributions().isEmpty()) {
            return;
        }

        Map<PointCategory, Long> awarded = applyDailyCategories(
                bucket.getUserId(),
                pointDay,
                bucket.getContributions()
        );
        long fixedPoints = 0;
        long oneTimePoints = 0;
        long dynamicPoints = 0;
        List<Map<String, Object>> breakdown = new ArrayList<>();

        for (Map.Entry<PointCategory, PointAwardBucket.Contribution> entry
                : bucket.getContributions().entrySet()) {
            PointCategory category = entry.getKey();
            PointAwardBucket.Contribution requested = entry.getValue();
            long categoryAwarded = awarded.getOrDefault(category, 0L);
            if (categoryAwarded <= 0) {
                continue;
            }
            switch (category.getComponent()) {
                case FIXED -> fixedPoints = safeAdd(fixedPoints, categoryAwarded);
                case ONE_TIME -> oneTimePoints = safeAdd(oneTimePoints, categoryAwarded);
                case DYNAMIC -> dynamicPoints = safeAdd(dynamicPoints, categoryAwarded);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", category.name());
            item.put("component", category.getComponent().name());
            item.put("requestedPoints", requested.getPoints());
            item.put("awardedPoints", categoryAwarded);
            item.put("itemCount", requested.getItemCount());
            item.put("rawValue", requested.getRawValue());
            breakdown.add(item);
        }

        long total = safeAdd(safeAdd(fixedPoints, oneTimePoints), dynamicPoints);
        if (total <= 0) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("breakdown", breakdown);
        details.put("requestedTotal", bucket.getContributions().values().stream()
                .mapToLong(PointAwardBucket.Contribution::getPoints)
                .reduce(0L, PointsService::safeAdd));

        int updated = jdbcTemplate.update(
                "UPDATE public.\"user\" SET score_bytes = score_bytes + ? WHERE id = ?",
                total,
                bucket.getUserId()
        );
        if (updated != 1) {
            throw new IllegalStateException("Unable to update user point balance");
        }

        jdbcTemplate.update(
                """
                INSERT INTO public.points_ledger (
                    user_id, event_id, occurred_at, event_type, points,
                    fixed_points, one_time_points, dynamic_points, details
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                bucket.getUserId(),
                bucket.getEventId(),
                occurredAt,
                bucket.getEventType(),
                total,
                fixedPoints,
                oneTimePoints,
                dynamicPoints,
                toJson(details)
        );
    }

    @Transactional
    public long adjustByAdmin(
            long userId,
            long operatorId,
            long delta,
            String reason
    ) throws BusinessException {
        if (delta == 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Point adjustment must not be zero"
            );
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank() || normalizedReason.length() > 500) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "A reason between 1 and 500 characters is required"
            );
        }

        List<Long> balances = jdbcTemplate.query(
                """
                SELECT score_bytes
                FROM public."user"
                WHERE id = ?
                FOR UPDATE
                """,
                (resultSet, rowNum) -> resultSet.getLong("score_bytes"),
                userId
        );
        if (balances.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "User not found");
        }

        long balanceBefore = balances.getFirst();
        long balanceAfter;
        try {
            balanceAfter = Math.addExact(balanceBefore, delta);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Point balance would exceed the supported long range"
            );
        }
        if (balanceAfter < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "The deduction exceeds the current point balance"
            );
        }

        jdbcTemplate.update(
                "UPDATE public.\"user\" SET score_bytes = ? WHERE id = ?",
                balanceAfter,
                userId
        );

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", normalizedReason);
        details.put("operatorId", operatorId);
        details.put("deltaPoints", delta);
        details.put("balanceBefore", balanceBefore);
        details.put("balanceAfter", balanceAfter);
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                INSERT INTO public.points_ledger (
                    user_id, event_id, occurred_at, event_type, points,
                    fixed_points, one_time_points, dynamic_points,
                    manual_points, details
                )
                VALUES (?, ?, ?, 'ADMIN_ADJUSTMENT', ?, 0, 0, 0, ?, ?::jsonb)
                """,
                userId,
                java.util.UUID.randomUUID(),
                occurredAt,
                delta,
                delta,
                toJson(details)
        );
        return balanceAfter;
    }

    private SubmittedIpResult markSubmittedIps(
            long userId,
            LocalDate pointDay,
            List<String> ruleMatchedIps,
            List<String> allIps
    ) {
        return jdbcTemplate.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            """
                            SELECT daily_unique, new_unique
                            FROM public.sparkle_mark_submitted_ips(
                                ?, ?, ?::jsonb, ?::jsonb
                            )
                            """
                    );
                    statement.setLong(1, userId);
                    statement.setObject(2, pointDay);
                    statement.setString(3, toJson(ruleMatchedIps));
                    statement.setString(4, toJson(allIps));
                    return statement;
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        return new SubmittedIpResult(0, 0);
                    }
                    return new SubmittedIpResult(
                            resultSet.getLong("daily_unique"),
                            resultSet.getLong("new_unique")
                    );
                }
        );
    }

    private List<ClaimResult> claimAwards(
            long userId,
            List<PointAwardBucket.Claim> claims
    ) {
        List<Map<String, Object>> payload = claims.stream().map(claim -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("claimType", claim.claimType());
            item.put("reference", claim.reference());
            item.put("category", claim.category().name());
            item.put("points", claim.points());
            return item;
        }).toList();
        return jdbcTemplate.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            """
                            SELECT result_category AS category, result_points AS points
                            FROM public.sparkle_claim_point_awards(?, ?::jsonb)
                            """
                    );
                    statement.setLong(1, userId);
                    statement.setString(2, toJson(payload));
                    return statement;
                },
                (resultSet, rowNum) -> new ClaimResult(
                        resultSet.getString("category"),
                        resultSet.getLong("points")
                )
        );
    }

    private Map<PointCategory, Long> applyDailyCategories(
            long userId,
            LocalDate pointDay,
            Map<PointCategory, PointAwardBucket.Contribution> contributions
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map.Entry<PointCategory, PointAwardBucket.Contribution> entry
                : contributions.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", entry.getKey().name());
            item.put("points", entry.getValue().getPoints());
            item.put("cap", pointRulesService.dailyCap(entry.getKey()));
            payload.add(item);
        }

        Map<PointCategory, Long> result = new EnumMap<>(PointCategory.class);
        jdbcTemplate.query(
                connection -> {
                    var statement = connection.prepareStatement(
                            """
                            SELECT result_category AS category, result_points AS awarded
                            FROM public.sparkle_apply_point_categories(?, ?, ?::jsonb)
                            """
                    );
                    statement.setLong(1, userId);
                    statement.setObject(2, pointDay);
                    statement.setString(3, toJson(payload));
                    return statement;
                },
                resultSet -> {
                    while (resultSet.next()) {
                        PointCategory category = parseCategory(
                                resultSet.getString("category")
                        );
                        if (category != null) {
                            result.put(category, resultSet.getLong("awarded"));
                        }
                    }
                    return null;
                }
        );
        return result;
    }

    private PointCategory parseCategory(String value) {
        try {
            return PointCategory.valueOf(value);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring unknown point category {}", value);
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize points payload", exception);
        }
    }

    private static long safeAdd(long first, long second) {
        if (second <= 0) {
            return first;
        }
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static long safeMultiply(long first, long second) {
        if (first <= 0 || second <= 0) {
            return 0;
        }
        return first > Long.MAX_VALUE / second
                ? Long.MAX_VALUE
                : first * second;
    }

    private record SubmittedIpResult(long dailyUnique, long newUnique) {
    }

    private record ClaimResult(String category, long points) {
    }
}
