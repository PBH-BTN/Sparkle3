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

    @Test
    void v34DeduplicatesBanConnectionsBeforeAggregatingCities() throws Exception {
        String resource = "db/migration/V34__deduplicate_bittorrent_traffic.sql";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("MAX(GREATEST(ban_history.to_peer_traffic, 0))"));
            assertTrue(sql.contains("GROUP BY"));
            assertTrue(sql.contains("dashboard_ban_city_traffic"));
            assertTrue(sql.contains("dashboard_ban_connection_traffic"));
            assertTrue(sql.contains("to_peer_traffic"));
            assertTrue(sql.contains("from_peer_traffic"));
            assertTrue(sql.contains("INTERVAL '7 days'"));
            assertFalse(sql.contains("INTERVAL '30 days'"));
            assertFalse(sql.contains("DROP MATERIALIZED VIEW"));
        }
    }

    @Test
    void v35RunsTrafficRefreshIncrementally() throws Exception {
        String resource = "db/migration/V35__incremental_bittorrent_traffic.sql";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("dashboard_bittorrent_traffic_refresh_state"));
            assertTrue(sql.contains("dashboard_ban_city_hourly_traffic"));
            assertTrue(sql.contains("dashboard_ban_connection_traffic_hourly_trigger"));
        }
    }
}
