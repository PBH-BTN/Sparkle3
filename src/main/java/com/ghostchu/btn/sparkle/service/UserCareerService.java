package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.LifetimeTraffic;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserCareerMapper;
import com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserCareerService {
    private final UserPublicProfileService profiles;
    private final UserCareerMapper mapper;
    private final UserLifetimeTrafficMapper traffic;

    public CareerView read(long viewer, long uid) throws BusinessException {
        var target = profiles.requireUser(uid);
        if (viewer != uid && profiles.privacySettings(target).lifetimeTrafficVisibility()
                != LifetimeTrafficVisibility.SHOW) return null;
        Map<String, String> metrics = new LinkedHashMap<>();
        for (var kind : new String[]{"ban", "feature", "swarm", "observation"}) {
            metrics.put(kind, "0");
            metrics.put(kind + "Recent", "0");
        }
        for (var row : mapper.counts(uid)) {
            metrics.put(row.get("kind"), row.get("total"));
            metrics.put(row.get("kind") + "Recent", row.get("recent"));
        }
        var durations = mapper.durations(uid);
        metrics.put("seeding", durations.get("seeding"));
        metrics.put("downloading", durations.get("downloading"));
        metrics.put("days", Long.toString(Math.max(0, ChronoUnit.DAYS.between(
                target.getRegisterAt().toInstant(), OffsetDateTime.now(ZoneOffset.UTC).toInstant()))));
        var last = mapper.latest(uid);
        String name = null;
        String observedAt = null;
        if (last != null) {
            long reporter = last.reporterId();
            // The shared public-profile policy applies username privacy and moderation.
            if (profiles.requireUser(reporter) != null) {
                name = profiles.profile(viewer, reporter).nickname();
            }
            observedAt = last.observedAt().toString();
        }
        return new CareerView(metrics, traffic.selectByUserId(uid), observedAt, name);
    }

    public record CareerView(Map<String, String> metrics, LifetimeTraffic traffic,
                             String lastObservedAt, String observerName) { }
}
