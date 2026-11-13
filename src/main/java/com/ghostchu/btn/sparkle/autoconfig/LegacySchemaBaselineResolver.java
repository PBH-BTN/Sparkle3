package com.ghostchu.btn.sparkle.autoconfig;

import org.flywaydb.core.api.FlywayException;

import java.util.Optional;
import java.util.Set;

/**
 * Resolves the last legacy db_sql version represented by an existing schema.
 * This class intentionally has no database dependencies so the adoption rules
 * can be unit tested without PostgreSQL.
 */
final class LegacySchemaBaselineResolver {
    static final Set<String> REQUIRED_CORE_TABLES = Set.of(
            "ban_history",
            "swarm_tracker",
            "torrent",
            "user",
            "userapp"
    );

    private static final Set<String> DASHBOARD_VIEWS = Set.of(
            "dashboard_ban_daily_peer",
            "dashboard_ban_daily_client",
            "dashboard_ban_daily_geo"
    );

    private static final Set<String> V28_INDEXES = Set.of(
            "dashboard_ban_daily_peer_bucket_dimension_idx",
            "dashboard_ban_daily_client_bucket_dimension_idx"
    );

    private LegacySchemaBaselineResolver() {
    }

    static Optional<String> resolve(boolean hasFlywayHistory,
                                    Set<String> relations,
                                    Set<String> columns,
                                    Set<String> indexes,
                                    boolean banHistoryHypertable,
                                    boolean dashboardRetentionPoliciesPresent) {
        if (hasFlywayHistory) {
            return Optional.empty();
        }
        if (relations.isEmpty()) {
            return Optional.empty();
        }
        if (!relations.containsAll(REQUIRED_CORE_TABLES)) {
            throw new FlywayException(
                    "The database schema is not empty but does not look like Sparkle3. "
                            + "Flyway refused to baseline unknown data."
            );
        }
        if (!banHistoryHypertable) {
            throw new FlywayException(
                    "The legacy Sparkle3 database has not completed the TimescaleDB conversion. "
                            + "Run the historical db_sql/v20-v22 scripts before starting this version."
            );
        }

        if (!relations.contains("app_config_entries")) {
            return Optional.of("22");
        }
        if (!relations.contains("announcements")) {
            return Optional.of("23");
        }
        if (!relations.contains("navigation_items")) {
            return Optional.of("24");
        }
        if (!relations.containsAll(DASHBOARD_VIEWS)) {
            return Optional.of("25");
        }
        if (!dashboardRetentionPoliciesPresent) {
            return Optional.of("26");
        }
        if (!indexes.containsAll(V28_INDEXES)) {
            return Optional.of("27");
        }
        if (!columns.contains("userapps_heartbeat.geoip")) {
            return Optional.of("28");
        }
        return Optional.of("29");
    }
}
