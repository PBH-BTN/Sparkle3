package com.ghostchu.btn.sparkle.autoconfig;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BittorrentTrafficMigrationTest {
    @Test
    void migrationDoesNotRaceWithContinuousAggregatePolicy() throws Exception {
        String resource = "db/migration/V33__bittorrent_traffic_statistics.sql";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(sql.contains("CALL refresh_continuous_aggregate("));
            assertTrue(sql.contains("timescaledb.materialized_only = false"));
        }
    }
}
