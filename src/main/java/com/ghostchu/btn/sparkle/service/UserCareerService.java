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
    @org.springframework.beans.factory.annotation.Autowired
    private CareerRedisCache cache;
    private final Object[] loadLocks = java.util.stream.IntStream.range(0,64).mapToObj(i -> new Object()).toArray();
    private final UserPublicProfileService profiles;
    private final UserCareerMapper mapper;
    private final UserLifetimeTrafficMapper traffic;

    public CareerView read(long viewer, long uid) throws BusinessException {
        var target = profiles.requireUser(uid);
        if (viewer != uid && profiles.privacySettings(target).lifetimeTrafficVisibility()
                != LifetimeTrafficVisibility.SHOW) return null;
        var snapshot = snapshot(uid);
        var metrics = new LinkedHashMap<>(snapshot.metrics());
        metrics.put("days", Long.toString(Math.max(0, ChronoUnit.DAYS.between(
                target.getRegisterAt().toInstant(), OffsetDateTime.now(ZoneOffset.UTC).toInstant()))));
        var last = snapshot.last();
        String name = null;
        if (last != null) {
            try { name = profiles.profile(viewer,last.reporterId()).nickname(); }
            catch (BusinessException failure) {
                if (failure.getStatusCode().value() != 404) throw failure;
            }
        }
        return new CareerView(metrics,snapshot.traffic(),last == null ? null : last.observedAt().toString(),name);
    }

    private Snapshot snapshot(long uid) {
        String key = cache == null ? null : cache.snapshotKey(uid);
        var cached = cache == null ? null : cache.get(key,Snapshot.class);
        if (cached != null) return cached;
        synchronized (loadLocks[Long.hashCode(uid) & 63]) {
            cached = cache == null ? null : cache.get(key,Snapshot.class);
            return cached == null ? load(uid,key) : cached;
        }
    }

    private Snapshot load(long uid,String key) {
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
        var snapshot = new Snapshot(metrics,traffic.selectByUserId(uid),mapper.latest(uid));
        if (cache != null) cache.put(key,snapshot,java.time.Duration.ofMinutes(5));
        return snapshot;
    }

    public record Snapshot(Map<String,String> metrics, LifetimeTraffic traffic, UserCareerMapper.Observation last) { }

    public record CareerView(Map<String, String> metrics, LifetimeTraffic traffic,
                             String lastObservedAt, String observerName) { }
}
