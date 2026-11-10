package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.RankingEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.RankingResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserSwarmStatisticsService;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        List<RankingEntry> entries = new ArrayList<>(Math.min(500, ranking.size()));
        for (var item : ranking) {
            var user = users.get(item.getUserId());
            if (user != null) {
                entries.add(toEntry(item, user));
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

    private RankingEntry toEntry(UserSwarmStatisticTrackRankingDto ranking,
                                 com.ghostchu.btn.sparkle.entity.User user) {
        var privacy = user.getPrivacyLevel();
        boolean anonymous = !privacy.isAllowFullUserName() && !privacy.isAllowBlurredUsername();
        String displayName = null;
        if (privacy.isAllowFullUserName()) {
            displayName = user.getNickname();
        } else if (privacy.isAllowBlurredUsername()) {
            displayName = blurNickname(user.getNickname());
        }
        boolean avatarAvailable = privacy.isAllowOriginalAvatar() || privacy.isAllowBlurredAvatar();
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
}
