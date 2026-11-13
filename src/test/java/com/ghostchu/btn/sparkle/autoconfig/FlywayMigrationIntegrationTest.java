package com.ghostchu.btn.sparkle.autoconfig;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a disposable PostgreSQL database with TimescaleDB installed.
 *
 * <p>This test is opt-in so the normal unit-test suite does not require Docker:
 * set SPARKLE_FLYWAY_TEST_URL, SPARKLE_FLYWAY_TEST_USER and
 * SPARKLE_FLYWAY_TEST_PASSWORD before running Gradle test. The database name
 * must contain "test"; set SPARKLE_FLYWAY_TEST_ALLOW_DESTRUCTIVE=true to
 * explicitly acknowledge that this database is disposable.</p>
 */
@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_ALLOW_DESTRUCTIVE", matches = "true")
class FlywayMigrationIntegrationTest {
    @Test
    void createsTheCompleteSchemaOnAnEmptyDatabase() throws Exception {
        String url = System.getenv("SPARKLE_FLYWAY_TEST_URL");
        String username = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_USER", "postgres");
        String password = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_PASSWORD", "");

        try (var connection = connect(url, username, password)) {
            requireDisposableDatabase(connection);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();

        assertFalse(Arrays.stream(flyway.info().all())
                .map(info -> info.getScript())
                .filter(java.util.Objects::nonNull)
                .anyMatch(script -> script.endsWith(".conf")));
        flyway.migrate();
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) = 4
                        AND bool_and(CASE WHEN column_name = 'btn_user_id' THEN is_nullable = 'YES' ELSE TRUE END)
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'pbh_licenses'
                      AND column_name IN ('btn_user_id', 'import_fingerprint', 'import_message_id', 'import_match')
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
            try (var result = statement.executeQuery("""
                    SELECT to_regclass('public.pbh_licenses_import_fingerprint_idx') IS NOT NULL
                        AND to_regclass('public.pbh_licenses_key_hash_idx') IS NOT NULL
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
        }

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '30' AND success")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM timescaledb_information.hypertables
                    WHERE hypertable_schema = 'public'
                      AND hypertable_name = 'ban_history'
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM timescaledb_information.continuous_aggregates
                    WHERE view_schema = 'public'
                      AND view_name IN (
                          'analyse_ban_history_hourly_basic',
                          'analyse_ban_history_daily_base',
                          'dashboard_ban_daily_peer',
                          'dashboard_ban_daily_client',
                          'dashboard_ban_daily_geo',
                          'dashboard_ban_hourly_traffic_city'
                      )
                    """)) {
                assertTrue(result.next());
                assertEquals(6, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        to_regclass('public.dashboard_swarm_hourly_traffic') IS NOT NULL
                        AND to_regclass('public.dashboard_ban_city_traffic') IS NOT NULL
                        AND to_regclass('public.dashboard_ban_connection_traffic') IS NOT NULL
                        AND to_regclass('public.dashboard_ban_city_hourly_traffic') IS NOT NULL
                        AND to_regclass('public.dashboard_bittorrent_traffic_refresh_state') IS NOT NULL
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'userapps_heartbeat'
                      AND column_name = 'geoip'
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'announcements'
                      AND column_name = 'placement'
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT to_regclass('public.user_contact_email_deliveries') IS NOT NULL
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        to_regclass('public.user_account_deletion_request') IS NOT NULL
                        AND to_regclass('public.user_rel_active_unique_idx') IS NOT NULL
                        AND to_regclass('public.user_rel_unique_idx') IS NULL
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        to_regclass('public.user_privacy_settings') IS NOT NULL
                        AND to_regclass('public.user_notification_preferences') IS NOT NULL
                        AND to_regclass('public.user_messages') IS NOT NULL
                        AND EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'user_messages'
                              AND column_name = 'dispatch_id'
                        )
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        to_regclass('public.community_groups') IS NOT NULL
                        AND to_regclass('public.community_group_github_teams') IS NOT NULL
                        AND to_regclass('public.afdian_bindings') IS NOT NULL
                        AND to_regclass('public.afdian_donations') IS NOT NULL
                        AND to_regclass('public.pbh_licenses') IS NOT NULL
                        AND to_regclass('public.member_platform_configs') IS NOT NULL
                        AND to_regclass('public.member_platform_bindings') IS NOT NULL
                        AND to_regclass('public.member_transactions') IS NOT NULL
                        AND to_regclass('public.currency_exchange_rates') IS NOT NULL
                        AND to_regclass('public.member_platform_sync_runs') IS NOT NULL
                        AND to_regclass('public.member_transaction_audit_events') IS NOT NULL
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        COUNT(*) FILTER (
                            WHERE table_name = 'member_platform_configs'
                              AND column_name IN ('auth_header', 'auth_scheme')
                        ) = 2
                        AND COUNT(*) FILTER (
                            WHERE table_name = 'member_transactions'
                              AND column_name IN (
                                  'remote_present', 'first_seen_at',
                                  'last_reconciled_at', 'last_sync_run_id',
                                  'last_changed_at', 'amount_base', 'base_currency'
                              )
                        ) = 7
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        to_regclass('public.points_ledger') IS NOT NULL
                        AND to_regclass('public.point_claims') IS NOT NULL
                        AND to_regclass('public.point_daily_ips') IS NULL
                        AND to_regclass('public.point_known_ips') IS NULL
                        AND to_regclass('public.swarm_traffic_delta') IS NOT NULL
                        AND to_regclass('public.swarm_hot_torrent_snapshot') IS NOT NULL
                        AND to_regclass('public.peer_version_snapshot') IS NOT NULL
                        AND EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'user'
                              AND column_name = 'score_bytes'
                        )
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM timescaledb_information.hypertables
                    WHERE hypertable_schema = 'public'
                      AND hypertable_name IN (
                          'points_ledger',
                          'swarm_traffic_delta',
                          'swarm_hot_torrent_snapshot',
                          'peer_version_snapshot'
                      )
                    """)) {
                assertTrue(result.next());
                assertEquals(4, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'points_ledger'
                      AND column_name = 'manual_points'
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            long testUserId;
            try (var result = statement.executeQuery("""
                    INSERT INTO public."user" (
                        avatar, email, nickname, register_at, last_login_at,
                        role, privacy_level
                    )
                    VALUES (
                        '/img/anonymous-user.png',
                        'points-migration-test@example.invalid',
                        'points-migration-test',
                        NOW(), NOW(), 'user', 2
                    )
                    RETURNING id
                    """)) {
                assertTrue(result.next());
                testUserId = result.getLong(1);
            }
            try (var result = statement.executeQuery("""
                    SELECT result_category AS awarded_category, result_points AS awarded
                    FROM public.sparkle_apply_point_categories(
                        %d,
                        CURRENT_DATE,
                        '[{"category":"LOGIN","points":1000,"cap":1000}]'::jsonb
                    )
                    """.formatted(testUserId))) {
                assertTrue(result.next());
                assertEquals("LOGIN", result.getString("awarded_category"));
                assertEquals(1000, result.getLong("awarded"));
            }
            statement.executeUpdate(
                    "DELETE FROM public.\"user\" WHERE id = " + testUserId
            );

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM pg_proc
                    WHERE pronamespace = 'public'::regnamespace
                      AND proname = 'sparkle_touch_point_app_activity'
                      AND pronargs = 4
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) = 0
                    FROM pg_proc
                    WHERE pronamespace = 'public'::regnamespace
                      AND proname IN (
                          'sparkle_mark_rule_hit_ips',
                          'sparkle_mark_submitted_ips'
                      )
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
        }
    }

    @Test
    void adoptsTheCurrentProductionSchemaWithoutReplayingHistoricalMigrations() throws Exception {
        String url = System.getenv("SPARKLE_FLYWAY_TEST_URL");
        String username = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_USER", "postgres");
        String password = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_PASSWORD", "");

        try (var connection = connect(url, username, password)) {
            requireDisposableDatabase(connection);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS dashboard_ban_daily_peer_bucket_idx
                    ON public.dashboard_ban_daily_peer (bucket DESC, peer_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS dashboard_ban_daily_client_bucket_idx
                    ON public.dashboard_ban_daily_client (bucket DESC, peer_client_name)
                    """);
            statement.execute("DROP TABLE public.flyway_schema_history");
        }

        new FlywayMigrationConfig(new MockEnvironment())
                .sparkleFlywayMigrationStrategy()
                .migrate(flyway);
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT version, type, description
                    FROM flyway_schema_history
                    WHERE version = '29'
                    """)) {
                assertTrue(result.next());
                assertEquals("BASELINE", result.getString("type"));
                assertEquals("Sparkle3 legacy schema 29", result.getString("description"));
            }

            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '30' AND success"
            )) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM pg_class
                    WHERE relkind = 'i'
                      AND relname IN (
                          'dashboard_ban_daily_peer_bucket_dimension_idx',
                          'dashboard_ban_daily_client_bucket_dimension_idx'
                      )
                    """)) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM pg_class
                    WHERE relkind = 'i'
                      AND relname IN (
                          'dashboard_ban_daily_peer_bucket_idx',
                          'dashboard_ban_daily_client_bucket_idx'
                      )
                    """)) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    private Connection connect(String url, String username, String password) throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private void requireDisposableDatabase(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next());
            String database = result.getString(1);
            if (!database.matches("(?i).*test.*")) {
                throw new AssertionError(
                        "Refusing to run destructive Flyway tests against database '" + database + "'"
                );
            }
        }
    }
}
