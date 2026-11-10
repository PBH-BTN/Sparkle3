package com.ghostchu.btn.sparkle.autoconfig;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySchemaBaselineResolverTest {
    private static final Set<String> CORE = Set.of(
            "ban_history",
            "swarm_tracker",
            "torrent",
            "user",
            "userapp"
    );
    private static final Set<String> CURRENT =
            union(CORE, Set.of(
                    "app_config_entries",
                    "announcements",
                    "navigation_items",
                    "dashboard_ban_daily_peer",
                    "dashboard_ban_daily_client",
                    "dashboard_ban_daily_geo"
            ));
    private static final Set<String> CURRENT_INDEXES = Set.of(
            "dashboard_ban_daily_peer_bucket_dimension_idx",
            "dashboard_ban_daily_client_bucket_dimension_idx"
    );

    @Test
    void usesInitialMigrationForAnEmptySchema() {
        assertTrue(resolve(Set.of(), Set.of(), Set.of(), false, false).isEmpty());
    }

    @Test
    void leavesExistingFlywayHistoryUntouched() {
        assertTrue(LegacySchemaBaselineResolver.resolve(
                true,
                CORE,
                Set.of(),
                Set.of(),
                false,
                false
        ).isEmpty());
    }

    @Test
    void detectsTheOldestSupportedTimescaleSchema() {
        assertEquals("22", resolve(CORE, Set.of(), Set.of(), true, false).orElseThrow());
    }

    @Test
    void resumesFromTheLastContiguousLegacyVersion() {
        assertEquals("23", resolve(
                union(CORE, Set.of("app_config_entries")),
                Set.of(),
                Set.of(),
                true,
                false
        ).orElseThrow());
    }

    @Test
    void detectsTheCurrentLegacySchema() {
        assertEquals("29", resolve(
                CURRENT,
                Set.of("userapps_heartbeat.geoip"),
                CURRENT_INDEXES,
                true,
                true
        ).orElseThrow());
    }

    @Test
    void doesNotSkipV28WhenTheGeoIpColumnExistsEarly() {
        assertEquals("27", resolve(
                CURRENT,
                Set.of("userapps_heartbeat.geoip"),
                Set.of(),
                true,
                true
        ).orElseThrow());
    }

    @Test
    void rejectsUnknownNonEmptySchema() {
        assertThrows(FlywayException.class, () -> resolve(
                Set.of("some_other_application_table"),
                Set.of(),
                Set.of(),
                false,
                false
        ));
    }

    @Test
    void rejectsLegacySchemaWithoutTimescaleConversion() {
        assertThrows(FlywayException.class, () -> resolve(CORE, Set.of(), Set.of(), false, false));
    }

    private java.util.Optional<String> resolve(Set<String> relations,
                                               Set<String> columns,
                                               Set<String> indexes,
                                               boolean hypertable,
                                               boolean retentionPolicies) {
        return LegacySchemaBaselineResolver.resolve(
                false,
                relations,
                columns,
                indexes,
                hypertable,
                retentionPolicies
        );
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.HashSet<String> result = new java.util.HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }
}
