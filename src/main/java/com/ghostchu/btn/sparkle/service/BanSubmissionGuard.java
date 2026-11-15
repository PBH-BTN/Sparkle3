package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.BanSubmissionModels.AppRank;
import com.ghostchu.btn.sparkle.controller.admin.dto.BanSubmissionModels.Event;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper.BlockedTarget;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper.EventQuery;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper.Fingerprint;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper.Target;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.google.common.collect.Lists;
import com.google.common.hash.Funnel;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BanSubmissionGuard {
    private static final int BATCH_SIZE = 500;
    private static final int CLEANUP_BATCH_SIZE = 5000;
    private static final String CLEANUP_CRON = "0 */10 * * * *";
    private static final String SECTION = "syncBanHistory";
    private static final Funnel<String> TEXT = (value, sink) -> {
        if (value == null) {
            sink.putInt(-1);
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            sink.putInt(bytes.length).putBytes(bytes);
        }
    };

    private final BanSubmissionGuardMapper mapper;
    private final BtnRuntimeConfigService config;

    private record Settings(long windowMillis, int threshold, boolean suppression) {
        OffsetDateTime quietCutoff(OffsetDateTime now) {
            return now.minus(Math.max(3 * windowMillis, 3_600_000L), ChronoUnit.MILLIS);
        }
    }

    private static final class Observation {
        private final Candidate candidate;
        private OffsetDateTime latest;
        private long replays;
        private long blocked;

        private Observation(Candidate candidate) {
            this.candidate = candidate;
        }

        private boolean needsState(OffsetDateTime cutoff) {
            return replays > 0 || latest != null && latest.isAfter(cutoff);
        }

        private Target row(String key) {
            return new Target(key, candidate.ip().getHostAddress(), candidate.ban().getPeerPort(),
                    truncate(candidate.ban().getModule()), truncate(candidate.ban().getRule()), latest, replays);
        }
    }

    public record Candidate(BtnBan ban, InetAddress ip, OffsetDateTime banAt) {}

    private record Entry(Candidate candidate, String target) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Candidate> filter(long appId, List<Candidate> candidates, OffsetDateTime now) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        var unique = new LinkedHashMap<Fingerprint, Entry>();
        var observations = new HashMap<String, Observation>();
        for (Candidate candidate : candidates) {
            String target = targetHash(candidate);
            var observation = observations.computeIfAbsent(target, ignored -> new Observation(candidate));
            if (unique.putIfAbsent(fingerprint(appId, target, candidate.banAt()), new Entry(candidate, target)) != null) {
                observation.replays++;
            }
        }

        var fingerprints = new ArrayList<>(unique.keySet());
        fingerprints.sort(Comparator.comparing(Fingerprint::day).thenComparing(Fingerprint::hash));
        var inserted = new HashSet<String>();
        for (var batch : Lists.partition(fingerprints, BATCH_SIZE)) {
            inserted.addAll(mapper.insertFingerprints(batch));
        }

        var newEntries = new ArrayList<Entry>();
        unique.forEach((fingerprint, entry) -> {
            var observation = observations.get(entry.target());
            if (inserted.contains(fingerprint.hash())) {
                newEntries.add(entry);
                var at = entry.candidate().banAt();
                if (observation.latest == null || at.isAfter(observation.latest)) {
                    observation.latest = at;
                }
            } else {
                observation.replays++;
            }
        });

        var settings = settings();
        var cutoff = settings.quietCutoff(now);
        var previousWindow = now.minus(settings.windowMillis(), ChronoUnit.MILLIS);
        var targets = observations.entrySet().stream()
                .filter(entry -> entry.getValue().needsState(cutoff))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().row(entry.getKey()))
                .toList();
        var blocked = new HashSet<String>();
        for (var batch : Lists.partition(targets, BATCH_SIZE)) {
            blocked.addAll(mapper.observe(appId, batch, now, cutoff, previousWindow,
                    settings.windowMillis(), settings.threshold(), settings.suppression()));
        }

        var accepted = new ArrayList<Candidate>(newEntries.size());
        for (Entry entry : newEntries) {
            if (blocked.contains(entry.target()) && entry.candidate().banAt().isAfter(cutoff)) {
                observations.get(entry.target()).blocked++;
            } else {
                accepted.add(entry.candidate());
            }
        }
        var counts = targets.stream()
                .filter(target -> observations.get(target.hash()).blocked > 0)
                .map(target -> new BlockedTarget(target.hash(), observations.get(target.hash()).blocked))
                .toList();
        for (var batch : Lists.partition(counts, BATCH_SIZE)) {
            mapper.recordSuppressed(appId, batch);
        }
        return accepted;
    }

    @Transactional(readOnly = true)
    public PageResult<Event> events(int page, int size, Long appId, String kind, String status,
                                    OffsetDateTime since, OffsetDateTime until) {
        int current = Math.max(1, page);
        int limit = Math.clamp(size, 1, 100);
        var settings = settings();
        var query = new EventQuery(appId, kind, status, since, until, settings.threshold(),
                settings.suppression(), settings.quietCutoff(OffsetDateTime.now(ZoneOffset.UTC)));
        long total = mapper.countEvents(query);
        long offset = (current - 1L) * limit;
        return new PageResult<>(offset < total ? mapper.listEvents(query, offset, limit) : List.of(),
                current, limit, total, (total + limit - 1) / limit);
    }

    @Transactional
    public int clearEvents() {
        return mapper.clearEvents();
    }

    public List<AppRank> rankApplications() {
        return mapper.rankApplications();
    }

    @Scheduled(cron = CLEANUP_CRON)
    @MonitoredScheduledTask(
            id = "ban-submission-cleanup",
            group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.ban-submission-cleanup.name",
            descriptionKey = "admin.tasks.items.ban-submission-cleanup.description",
            expression = CLEANUP_CRON
    )
    public void cleanup() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var stateCutoff = settings().quietCutoff(now);
        var eventCutoff = now.minusDays(30);
        // The accepted ban timestamp can be at most seven days old.
        var chunks = mapper.dropExpiredFingerprints(now.minusDays(8));
        int removed = 0;
        for (int batch = 0; batch < 20; batch++) {
            int count = mapper.deleteExpiredTargets(stateCutoff, eventCutoff, CLEANUP_BATCH_SIZE);
            removed += count;
            if (count < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        if (chunks.isEmpty() && removed == 0) {
            MonitoredTaskOutcome.skipped("No expired ban submission records");
        }
    }

    private Settings settings() {
        return new Settings(config.longValue(SECTION, "interval"),
                (int) config.longValue(SECTION, "repeatBanWindows"),
                config.booleanValue(SECTION, "repeatBanSuppression"));
    }

    private static String targetHash(Candidate candidate) {
        var ban = candidate.ban();
        return asUuid(Hashing.sha256().newHasher()
                .putObject(candidate.ip().getHostAddress(), TEXT)
                .putInt(ban.getPeerPort())
                .putObject(ban.getTorrentIdentifier(), TEXT)
                .putObject(ban.getModule(), TEXT)
                .putObject(ban.getRule(), TEXT).hash().asBytes());
    }

    private static Fingerprint fingerprint(long appId, String target, OffsetDateTime at) {
        var instant = at.toInstant();
        String hash = asUuid(Hashing.sha256().newHasher().putLong(appId)
                .putUnencodedChars(target).putLong(instant.getEpochSecond()).putInt(instant.getNano())
                .hash().asBytes());
        return new Fingerprint(at.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS), hash);
    }

    private static String asUuid(byte[] hash) {
        var bytes = ByteBuffer.wrap(hash);
        return new UUID(bytes.getLong(), bytes.getLong()).toString();
    }

    private static String truncate(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= 128) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, 128));
    }
}
