package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.BasicInfo;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.Points;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.PointsRanking;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.Rankings;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.Traffic7d;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.TrafficRanking;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.UserDataResponse;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserSwarmStatistic;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import com.ghostchu.btn.sparkle.service.dto.UserSwarmStatisticTrackRankingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FlarumIntegrationService {
    private static final Pattern GITHUB_UID = Pattern.compile("^[0-9]{1,32}$");

    private final UserRelMapper userRelMapper;
    private final IUserService userService;
    private final IUserSwarmStatisticsService swarmStatisticsService;
    private final AdminConfigService adminConfigService;

    public UserDataResponse findByGithubUid(String githubUid) throws BusinessException {
        String normalizedGithubUid = githubUid == null ? "" : githubUid.trim();
        if (!GITHUB_UID.matcher(normalizedGithubUid).matches()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "githubUid must contain 1 to 32 digits"
            );
        }

        var relation = userRelMapper.findUserRelByPlatformUserId(
                "github",
                normalizedGithubUid
        );
        if (relation == null) {
            return UserDataResponse.unbound(normalizedGithubUid);
        }

        User user = userService.getById(relation.getBindUserId());
        if (user == null) {
            return UserDataResponse.unbound(normalizedGithubUid);
        }

        var config = adminConfigService.get();
        boolean statisticsEnabled = config.swarmStatisticsEnabled();
        UserSwarmStatistic statistics = statisticsEnabled
                ? swarmStatisticsService.getById(user.getId())
                : null;

        Traffic7d traffic7d = new Traffic7d(
                statisticsEnabled,
                config.swarmStatisticsWindowDays(),
                value(statistics == null ? null : statistics.getSentTrafficSelfReport()),
                value(statistics == null ? null : statistics.getReceivedTrafficSelfReport()),
                value(statistics == null ? null : statistics.getSentTrafficOtherAck()),
                value(statistics == null ? null : statistics.getReceivedTrafficOtherAck()),
                statistics == null ? null : statistics.getLastUpdateAt()
        );

        long scoreBytes = user.getScoreBytes() == null ? 0L : user.getScoreBytes();
        Points points = new Points(Long.toString(scoreBytes));
        PointsRanking pointsRanking = pointsRanking(user, scoreBytes);
        TrafficRanking trafficRanking = statisticsEnabled
                ? trafficRanking(swarmStatisticsService.getUserRanking(user.getId()))
                : null;

        return new UserDataResponse(
                true,
                normalizedGithubUid,
                Long.toString(user.getId()),
                new BasicInfo(
                        Long.toString(user.getId()),
                        user.getNickname(),
                        user.getRegisterAt(),
                        user.getLastLoginAt(),
                        user.getBannedAt() != null
                ),
                traffic7d,
                points,
                new Rankings(trafficRanking, pointsRanking),
                OffsetDateTime.now()
        );
    }

    private TrafficRanking trafficRanking(UserSwarmStatisticTrackRankingDto ranking) {
        if (ranking == null) {
            return null;
        }
        return new TrafficRanking(
                ranking.getRank(),
                value(ranking.getSentTrafficSelfReport()),
                value(ranking.getReceivedTrafficSelfReport()),
                value(ranking.getSentTrafficOtherAck()),
                value(ranking.getReceivedTrafficOtherAck())
        );
    }

    private PointsRanking pointsRanking(User user, long scoreBytes) {
        List<Long> systemUids = userService.getSystemUids();
        if (systemUids.contains(user.getId())) {
            return new PointsRanking(null, Long.toString(scoreBytes));
        }

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .and(query -> query.gt("score_bytes", scoreBytes)
                                .or(nested -> nested.eq("score_bytes", scoreBytes)
                                        .lt("id", user.getId())));
        if (!systemUids.isEmpty()) {
            wrapper.notIn("id", systemUids);
        }
        long rank = userService.count(wrapper) + 1;
        return new PointsRanking(rank, Long.toString(scoreBytes));
    }

    private String value(Long value) {
        return Long.toString(value == null ? 0L : value);
    }

}
