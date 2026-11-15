package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.HomepageIpFlagModels.DailyIpFlags;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.mapper.HomepageIpFlagMapper;
import com.ghostchu.btn.sparkle.mapper.HomepageIpFlagMapper.Observation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class HomepageIpFlagService {
    // Persisted bit indexes and metric IDs must match the V80 bootstrap. Append new metrics only.
    private static final List<String> METRICS = List.of(
            "total", "incoming", "peerChoked", "peerUnchoked", "clientChoked", "clientUnchoked",
            "notInterested", "unknown", "optimisticUnchoke", "dht", "pex", "lsd",
            "trackerOrOther", "rc4", "plaintext", "utp", "tcp"
    );
    private static final String FLAG_MARKERS = " IdDuUK?OHXL EeP ";
    private static final int OBSERVE_LIMIT = 500;
    private static final int PUBLISH_LIMIT = 5_000;

    private final HomepageIpFlagMapper mapper;

    public void observe(List<SwarmTracker> rows) {
        // Group by partition and keep lock acquisition ordered across batch boundaries.
        var observations = new TreeMap<LocalDate, Map<String, Integer>>();
        for (var row : rows) {
            var day = row.getLastTimeSeen().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
            observations.computeIfAbsent(day, ignored -> new TreeMap<>())
                    .merge(row.getPeerIp().getHostAddress(), mask(row.getFlags()), (left, right) -> left | right);
        }
        var batch = new ArrayList<Observation>(OBSERVE_LIMIT);
        for (var day : observations.entrySet()) {
            for (var entry : day.getValue().entrySet()) {
                batch.add(new Observation(entry.getKey(), entry.getValue()));
                if (batch.size() == OBSERVE_LIMIT) {
                    mapper.observe(day.getKey(), batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                mapper.observe(day.getKey(), batch);
                batch.clear();
            }
        }
    }

    static int mask(String flags) {
        int bits = 1;
        if (flags == null || flags.isBlank()) {
            return bits;
        }
        for (int i = 1; i < FLAG_MARKERS.length(); i++) {
            char marker = FLAG_MARKERS.charAt(i);
            if (marker != ' ' && flags.indexOf(marker) >= 0) {
                bits |= 1 << i;
            }
        }
        // BTN's serialized flags omit an explicit Tracker source and TCP marker.
        if ((bits & ((1 << 9) | (1 << 10) | (1 << 11))) == 0) {
            bits |= 1 << 12;
        }
        if ((bits & (1 << 15)) == 0) {
            bits |= 1 << 16;
        }
        return bits;
    }

    public boolean publish() {
        int published = 0;
        for (int batch = 0; batch < 20; batch++) {
            int count = mapper.publish(PUBLISH_LIMIT, METRICS.size());
            published += count;
            if (count < PUBLISH_LIMIT) {
                break;
            }
        }
        var cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(14);
        // Accepted timestamps can be at most seven days late, so older state cannot become newly dirty.
        boolean pruned = !mapper.hasOlderPending(cutoff) && !mapper.prune(cutoff).isEmpty();
        return published > 0 || pruned;
    }

    public List<DailyIpFlags> daily(LocalDate from, LocalDate to) {
        var days = new LinkedHashMap<LocalDate, Map<String, Long>>();
        for (var row : mapper.daily(from, to)) {
            days.computeIfAbsent(row.day(), ignored -> new LinkedHashMap<>())
                    .put(METRICS.get(row.metric()), row.count());
        }
        return days.entrySet().stream()
                .map(entry -> new DailyIpFlags(entry.getKey().toString(), entry.getValue()))
                .toList();
    }
}
