package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.RankingEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.RankingResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PointsRankingEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PointsRankingResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticsService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.UserPrivacySettings;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui/ranking")
@RequiredArgsConstructor
public class RankingApiController {
    private final IUserService userService;
    private final IUserSwarmStatisticsService userSwarmStatisticsService;
    private final AdminConfigService adminConfigService;
    private final JdbcTemplate jdbcTemplate;
    private final UserPrivacyService privacyService;

    @GetMapping
    public RankingResponse ranking(@AuthenticationPrincipal SparkleUserDetails userDetails)
            throws BusinessException {
        if (!adminConfigService.get().swarmStatisticsEnabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Swarm statistics are disabled");
        }
        var ranking = userSwarmStatisticsService.getUsersRanking().stream().limit(500).toList();
        var userIds = ranking.stream()
                .map(UserSwarmStatisticTrackRankingDto::getUserId)
                .distinct()
                .toList();
        Map<Long, com.ghostchu.btn.sparkle.entity.User> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(user -> users.put(user.getId(), user));
        }
        Map<Long, UserPrivacySettings> privacy = privacyService.getAll(userIds);
        List<RankingEntry> entries = new ArrayList<>(Math.min(500, ranking.size()));
        for (var item : ranking) {
            var user = users.get(item.getUserId());
            if (user != null) {
                entries.add(toEntry(
                        item,
                        user,
                        privacy.getOrDefault(
                                user.getId(),
                                UserPrivacySettings.defaults(user.getId())
                        )
                ));
            }
        }
        RankingEntry self = null;
        if (userDetails != null) {
            var selfRanking = userSwarmStatisticsService.getUserRanking(userDetails.getUserId());
            if (selfRanking != null) {
                self = new RankingEntry(
                        selfRanking.getRank(),
                        selfRanking.getUserId(),
                        null,
                        false,
                        null,
                        false,
                        false,
                        value(selfRanking.getSentTrafficSelfReport()),
                        value(selfRanking.getReceivedTrafficSelfReport()),
                        value(selfRanking.getSentTrafficOtherAck()),
                        value(selfRanking.getReceivedTrafficOtherAck())
                );
            }
        }
        return new RankingResponse(self, entries);
    }

    @GetMapping("/points")
    public PointsRankingResponse pointsRanking(
            @AuthenticationPrincipal SparkleUserDetails userDetails
    ) {
        List<Long> systemUids = userService.getSystemUids();
        String exclusion = notInClause("u.id", systemUids.size());
        Object[] exclusionArgs = systemUids.toArray();
        List<PointsRankRow> ranked = jdbcTemplate.query(
                """
                SELECT u.id, ROW_NUMBER() OVER (
                    ORDER BY u.score_bytes DESC, u.id ASC
                ) AS ranking
                FROM public."user" u
                LEFT JOIN public.user_privacy_settings ps ON ps.user_id = u.id
                WHERE COALESCE(ps.points_visibility, 'SHOW') = 'SHOW' %s
                ORDER BY u.score_bytes DESC, u.id ASC
                LIMIT 500
                """.formatted(exclusion),
                (resultSet, rowNum) -> new PointsRankRow(
                        resultSet.getLong("id"),
                        resultSet.getLong("ranking")
                ),
                exclusionArgs
        );
        List<Long> userIds = ranked.stream().map(PointsRankRow::userId).toList();
        Map<Long, com.ghostchu.btn.sparkle.entity.User> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(user -> users.put(user.getId(), user));
        }
        Map<Long, UserPrivacySettings> privacy = privacyService.getAll(userIds);
        List<PointsRankingEntry> entries = new ArrayList<>(ranked.size());
        for (PointsRankRow row : ranked) {
            var user = users.get(row.userId());
            if (user != null && !systemUids.contains(user.getId())) {
                entries.add(toPointsEntry(
                        row,
                        user,
                        privacy.getOrDefault(
                                user.getId(),
                                UserPrivacySettings.defaults(user.getId())
                        )
                ));
            }
        }

        PointsRankingEntry self = null;
        if (userDetails != null && !systemUids.contains(userDetails.getUserId())) {
            var user = userService.getById(userDetails.getUserId());
            if (user != null && privacyService.get(user.getId()).showsPoints()) {
                long score = value(user.getScoreBytes());
                String selfExclusion = notInClause("u.id", systemUids.size());
                List<Object> args = new ArrayList<>();
                args.add(score);
                args.add(score);
                args.add(user.getId());
                args.addAll(systemUids);
                Long rank = jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) + 1
                        FROM public."user" u
                        LEFT JOIN public.user_privacy_settings ps ON ps.user_id = u.id
                        WHERE (
                            u.score_bytes > ?
                            OR (u.score_bytes = ? AND u.id < ?)
                        )
                          AND COALESCE(ps.points_visibility, 'SHOW') = 'SHOW' %s
                        """.formatted(selfExclusion),
                        Long.class,
                        args.toArray()
                );
                self = new PointsRankingEntry(
                        rank == null ? 0 : rank,
                        user.getId(),
                        null,
                        false,
                        null,
                        false,
                        user.getBannedAt() != null,
                        Long.toString(score)
                );
            }
        }
        return new PointsRankingResponse(self, entries);
    }

    private RankingEntry toEntry(UserSwarmStatisticTrackRankingDto ranking,
                                 com.ghostchu.btn.sparkle.entity.User user,
                                 UserPrivacySettings privacy) {
        boolean anonymous = !privacy.allowsFullUsername() && !privacy.allowsBlurredUsername();
        String displayName = null;
        if (privacy.allowsFullUsername()) {
            displayName = user.getNickname();
        } else if (privacy.allowsBlurredUsername()) {
            displayName = blurNickname(user.getNickname());
        }
        boolean avatarAvailable = !privacy.hidesAvatar();
        return new RankingEntry(
                ranking.getRank(),
                ranking.getUserId(),
                displayName,
                anonymous,
                avatarAvailable ? user.getAvatarUrl() : null,
                avatarAvailable,
                user.getBannedAt() != null,
                value(ranking.getSentTrafficSelfReport()),
                value(ranking.getReceivedTrafficSelfReport()),
                value(ranking.getSentTrafficOtherAck()),
                value(ranking.getReceivedTrafficOtherAck())
        );
    }

    private String blurNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "***";
        }
        if (nickname.length() <= 4) {
            return "*".repeat(nickname.length());
        }
        return nickname.substring(0, 3)
                + "*".repeat(nickname.length() - 4)
                + nickname.substring(nickname.length() - 1);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private PointsRankingEntry toPointsEntry(
            PointsRankRow ranking,
            com.ghostchu.btn.sparkle.entity.User user,
            UserPrivacySettings privacy
    ) {
        boolean anonymous = !privacy.allowsFullUsername()
                && !privacy.allowsBlurredUsername();
        String displayName = null;
        if (privacy.allowsFullUsername()) {
            displayName = user.getNickname();
        } else if (privacy.allowsBlurredUsername()) {
            displayName = blurNickname(user.getNickname());
        }
        boolean avatarAvailable = !privacy.hidesAvatar();
        return new PointsRankingEntry(
                ranking.rank(),
                user.getId(),
                displayName,
                anonymous,
                avatarAvailable ? user.getAvatarUrl() : null,
                avatarAvailable,
                user.getBannedAt() != null,
                Long.toString(value(user.getScoreBytes()))
        );
    }

    private String notInClause(String column, int size) {
        if (size == 0) {
            return "";
        }
        return "AND " + column + " NOT IN ("
                + java.util.stream.IntStream.range(0, size)
                .mapToObj(ignored -> "?")
                .collect(java.util.stream.Collectors.joining(", "))
                + ")";
    }

    private record PointsRankRow(long userId, long rank) {
    }
}
