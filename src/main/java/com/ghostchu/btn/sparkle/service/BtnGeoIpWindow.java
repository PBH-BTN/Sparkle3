package com.ghostchu.btn.sparkle.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BtnGeoIpWindow {
    private BtnGeoIpWindow() {
    }

    static List<Hit> detect(List<Observation> ordered, Duration window, int threshold, boolean splitFamilies) {
        if (!splitFamilies) {
            Hit hit = find(ordered, window, threshold, 0);
            return hit == null ? List.of() : List.of(hit);
        }

        List<Hit> result = new ArrayList<>(2);
        for (int family : List.of(4, 6)) {
            Hit hit = find(ordered.stream().filter(row -> row.family() == family).toList(), window, threshold, family);
            if (hit != null) {
                result.add(hit);
            }
        }
        return result;
    }

    private static Hit find(List<Observation> ordered, Duration window, int threshold, int family) {
        Map<String, Map<String, Integer>> countries = new HashMap<>();
        int earlier = 0;
        int regions = 0;
        int pending = 0;
        for (int later = 0; later < ordered.size(); later++) {
            Observation newest = ordered.get(later);
            OffsetDateTime cutoff = newest.time().minus(window);
            while (earlier < later && ordered.get(earlier).time().isBefore(cutoff)) {
                Observation expired = ordered.get(earlier++);
                regions += adjust(countries, expired, -1);
                if (expired.pending()) {
                    pending--;
                }
            }
            regions += adjust(countries, newest, 1);
            if (newest.pending()) {
                pending++;
            }
            if (pending > 0 && regions >= threshold) {
                return new Hit(family, regions, ordered.get(earlier).time(), newest.time(),
                        countries.entrySet().stream()
                                .flatMap(entry -> entry.getValue().size() == 1 && entry.getValue().containsKey("")
                                        ? java.util.stream.Stream.of(entry.getKey())
                                        : entry.getValue().keySet().stream().filter(province -> !province.isEmpty())
                                                .map(province -> entry.getKey() + "/" + province))
                                .sorted().limit(12).toList());
            }
        }
        return null;
    }

    private static int adjust(Map<String, Map<String, Integer>> countries, Observation row, int delta) {
        Map<String, Integer> provinces = countries.computeIfAbsent(row.country(), ignored -> new HashMap<>());
        int before = regionCount(provinces);
        int count = Objects.requireNonNullElse(provinces.get(row.province()), 0) + delta;
        if (count == 0) {
            provinces.remove(row.province());
        } else {
            provinces.put(row.province(), count);
        }
        if (provinces.isEmpty()) {
            countries.remove(row.country());
        }
        return regionCount(provinces) - before;
    }

    private static int regionCount(Map<String, Integer> provinces) {
        // Missing province data counts as the country only when no known province represents it.
        return provinces.isEmpty() ? 0 : Math.max(1, provinces.size() - (provinces.containsKey("") ? 1 : 0));
    }

    record Observation(long id, long applicationId, OffsetDateTime time, int family,
                       String country, String province, boolean pending) {}

    record Hit(int family, int regions, OffsetDateTime first, OffsetDateTime last, List<String> evidence) {}
}
