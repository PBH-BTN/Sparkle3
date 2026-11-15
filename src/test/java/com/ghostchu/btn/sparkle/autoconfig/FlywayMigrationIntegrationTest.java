package com.ghostchu.btn.sparkle.autoconfig;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.ghostchu.btn.sparkle.controller.admin.AdminBanSubmissionController;
import com.ghostchu.btn.sparkle.controller.admin.AdminContentModerationController;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper;
import com.ghostchu.btn.sparkle.mapper.ContentModerationMapper;
import com.ghostchu.btn.sparkle.service.moderation.ContentModerationService;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.BanSubmissionGuard;
import com.ghostchu.btn.sparkle.service.BanSubmissionGuard.Candidate;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private final String sourceUrl = System.getenv("SPARKLE_FLYWAY_TEST_URL");
    private final String username = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_USER", "postgres");
    private final String password = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_PASSWORD", "");
    private String database;
    private String url;

    @BeforeEach
    void createDatabase() throws Exception {
        try (var connection = connect(sourceUrl, username, password);
             var statement = connection.createStatement()) {
            requireDisposableDatabase(connection);
            String name = "sparkle_flyway_test_" + UUID.randomUUID().toString().replace("-", "");
            statement.execute("CREATE DATABASE " + name);
            database = name;
            String[] parts = sourceUrl.split("\\?", 2);
            url = parts[0].substring(0, parts[0].lastIndexOf('/') + 1) + database
                    + (parts.length == 2 ? "?" + parts[1] : "");
        }
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        if (database == null) return;
        try (var connection = connect(sourceUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE " + database);
        }
    }

    @Test
    void contentModerationDictionaryPublishesCommittedVersionsAndRejectsStaleEdits() throws Exception {
        var flyway = Flyway.configure().dataSource(url, username, password)
                .baselineVersion("76").target("77").load();
        flyway.baseline();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        var configuration = new Configuration(new Environment("content-review-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.addMapper(ContentModerationMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var primary = factory.openSession(true); var replica = factory.openSession(true)) {
            var service = new ContentModerationService(primary.getMapper(ContentModerationMapper.class));
            var secondInstance = new ContentModerationService(replica.getMapper(ContentModerationMapper.class));
            service.run(null);
            secondInstance.run(null);
            String words = "# 说明\n第一条\n";
            var mvc = MockMvcBuilders.standaloneSetup(new AdminContentModerationController(service))
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .build();
            var admin = new SparkleUserDetails(new User().setId(100L).setRole("admin"), null);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
            try {
                mvc.perform(put("/api/admin/content-moderation").contentType("application/json")
                                .content("{\"version\":\"0\",\"enabled\":true,\"words\":\"# 说明\\n第一条\\n\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.activeVersion").value("1"));
                mvc.perform(get("/api/admin/content-moderation"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.words").value(words));
            } finally {
                SecurityContextHolder.clearContext();
            }
            assertEquals(words, service.settings().words());
            assertTrue(service.snapshot().matches("第一条记录"));
            assertFalse(secondInstance.snapshot().matches("第一条记录"));
            replica.clearCache();
            secondInstance.refresh();
            assertTrue(secondInstance.snapshot().matchesJson(java.util.Map.of("nested", List.of("第一条"))));
            assertThrows(BusinessException.class, () -> secondInstance.save("0", false, "", 100L));

            var original = secondInstance.snapshot();
            service.save("1", false, words, 100L);
            replica.clearCache();
            secondInstance.refresh();
            assertFalse(secondInstance.snapshot().matches("第一条记录"));
            assertTrue(original.matches("第一条记录"));
        }
    }

    @Test
    void banSubmissionGuardDeduplicatesAndDetectsAcrossWindows() throws Exception {
        try (var connection = connect(url, username, password); var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
            statement.execute(new org.springframework.core.io.ClassPathResource("db/migration/V23__app_config_entries.sql")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        var flyway = Flyway.configure().dataSource(url, username, password)
                .baselineVersion("73").target("79").load();
        flyway.baseline();
        assertEquals(6, flyway.migrate().migrationsExecuted);
        flyway.validate();

        var configuration = new Configuration(new Environment("ban-guard-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        configuration.addInterceptor(interceptor);
        String resource = "mapper/BanSubmissionGuardMapper.xml";
        try (var input = getClass().getResourceAsStream("/" + resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        var config = mock(BtnRuntimeConfigService.class);
        when(config.longValue("syncBanHistory", "interval")).thenReturn(10_000L);
        when(config.longValue("syncBanHistory", "repeatBanWindows")).thenReturn(3L);
        var start = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10).withNano(0);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(BanSubmissionGuardMapper.class);
            var guard = new BanSubmissionGuard(mapper, config);
            var first = banCandidate(start, "2001:4860::8888");
            assertEquals(1, guard.filter(10, List.of(first, banCandidate(start, "2001:4860:0:0:0:0:0:8888")), start).size());
            session.rollback();
            assertEquals(1, guard.filter(10, List.of(first), start).size());
            session.commit();
            assertTrue(guard.filter(10, List.of(first), start.plusSeconds(10)).isEmpty());
            assertEquals(1, guard.filter(20, List.of(first), start.plusSeconds(10)).size());
            assertEquals(2, guard.filter(10, List.of(banCandidate(start.plusSeconds(10), "2001:4860::8888"),
                    banCandidate(start.plusSeconds(20), "2001:4860::8888")), start.plusSeconds(20)).size());
            assertEquals(1, guard.filter(10, List.of(banCandidate(start.plusSeconds(30), "2001:4860::8888")),
                    start.plusSeconds(30)).size());
            var observed = guard.events(1, 20, 10L, "repeated", "observing", start, null).records().getFirst();
            assertEquals(3, observed.peakWindows());
            assertEquals(1, observed.replays());
            assertEquals("2001:4860::/64", observed.peerNetwork());
            assertEquals(0, observed.suppressed());

            when(config.booleanValue("syncBanHistory", "repeatBanSuppression")).thenReturn(true);
            assertTrue(guard.filter(10, List.of(banCandidate(start.plusSeconds(40), "2001:4860::8888")),
                    start.plusSeconds(40)).isEmpty());
            var blocked = guard.events(1, 20, 10L, null, "blocking", null, null).records().getFirst();
            assertEquals(1, blocked.suppressed());
            var rank = guard.rankApplications().getFirst();
            assertEquals("10", rank.appId());
            assertEquals(1, rank.replayBatches());
            assertEquals(1, rank.replays());
            assertTrue(guard.filter(20, List.of(first, first, first), start.plusSeconds(40)).isEmpty());
            var ranks = guard.rankApplications();
            assertEquals(List.of("20", "10"), ranks.stream().map(row -> row.appId()).toList());
            assertEquals(1, ranks.getFirst().replayBatches());
            assertEquals(3, ranks.getFirst().replays());
            assertTrue(guard.filter(10, List.of(first), start.plusSeconds(41)).isEmpty());
            MockMvcBuilders.standaloneSetup(new AdminBanSubmissionController(guard)).build()
                    .perform(get("/api/admin/ban-submissions/ranking"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].appId").value("10"))
                    .andExpect(jsonPath("$[0].replayBatches").value(2))
                    .andExpect(jsonPath("$[0].replays").value(2));
            var afterQuiet = start.plusHours(2);
            assertEquals(1, guard.filter(10, List.of(banCandidate(afterQuiet, "2001:4860::8888")), afterQuiet).size());
            assertEquals(1, guard.events(1, 20, 10L, null, "quiet", null, null).total());
            assertTrue(mapper.dropExpiredFingerprints(start.minusDays(8)).isEmpty());
            assertEquals(0, mapper.deleteExpiredTargets(start.minusDays(4), start.minusDays(30), 5000));
        }

        var batch = IntStream.range(0, 501).mapToObj(port -> {
            var candidate = banCandidate(start, "1.1.1.1");
            candidate.ban().setPeerPort(10_000 + port);
            return candidate;
        }).toList();
        Callable<Integer> submit = () -> {
            try (var session = factory.openSession()) {
                var guard = new BanSubmissionGuard(session.getMapper(BanSubmissionGuardMapper.class), config);
                int accepted = guard.filter(30, batch, start).size();
                session.commit();
                return accepted;
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            assertEquals(batch.size(), first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
        }
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(BanSubmissionGuardMapper.class);
            var guard = new BanSubmissionGuard(mapper, config);
            var events = guard.events(2, 20, 30L, "replay", "replayed", null, null);
            assertEquals(batch.size(), events.total());
            assertEquals(20, events.records().size());
            assertEquals(1, events.records().getFirst().replays());
            var mvc = MockMvcBuilders.standaloneSetup(new AdminBanSubmissionController(guard))
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .build();
            mvc.perform(get("/api/admin/ban-submissions").param("appId", "30").param("kind", "replay")
                            .param("page", "2").param("size", "20").param("since", start.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(501))
                    .andExpect(jsonPath("$.records[0].appId").value("30"))
                    .andExpect(jsonPath("$.records[0].peerNetwork").value("1.1.1.0/24"));
            assertEquals(1, mapper.deleteExpiredTargets(start.plusDays(1), start.minusDays(29), 5000));
            assertEquals(batch.size(), mapper.deleteExpiredTargets(start.plusDays(31), start.plusDays(1), 5000));
            session.rollback();

            var admin = new SparkleUserDetails(new User().setId(1L).setRole("admin"), null);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
            try {
                mvc.perform(delete("/api/admin/ban-submissions").param("appId", "999"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.deleted").value(batch.size()));
            } finally {
                SecurityContextHolder.clearContext();
            }
            assertEquals(0, guard.events(1, 20, null, null, null, null, null).total());
            assertTrue(guard.rankApplications().isEmpty());
            assertEquals(0, guard.clearEvents());
            assertTrue(guard.filter(30, List.of(batch.getFirst()), start.plusSeconds(10)).isEmpty());
            assertEquals(1, guard.events(1, 20, 30L, "replay", null, null, null).total());
        }
    }

    private static Candidate banCandidate(OffsetDateTime time, String ip) {
        var ban = new BtnBan();
        ban.setTorrentIdentifier("torrent");
        ban.setPeerPort(8080);
        ban.setModule("test-module");
        return new Candidate(ban, InetAddress.ofLiteral(ip), time);
    }

    @Test
    void createsTheCompleteSchemaOnAnEmptyDatabase() throws Exception {
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
                    SELECT COUNT(*) FILTER (WHERE column_name = 'userid') = 1
                        AND COUNT(*) FILTER (WHERE column_name IN ('afdian_user_id', 'external_user_id')) = 0
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'pbh_licenses'
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
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
                          'dashboard_ban_daily_geo'
                      )
                    """)) {
                assertTrue(result.next());
                assertEquals(5, result.getInt(1));
            }

            try (var result = statement.executeQuery("""
                    SELECT
                        to_regclass('public.dashboard_ban_hourly_traffic_city') IS NULL
                        AND to_regclass('public.dashboard_swarm_hourly_traffic') IS NULL
                        AND to_regclass('public.dashboard_ban_city_traffic') IS NULL
                        AND to_regclass('public.dashboard_ban_connection_traffic') IS NULL
                        AND to_regclass('public.dashboard_ban_city_hourly_traffic') IS NULL
                        AND to_regclass('public.dashboard_bittorrent_traffic_refresh_state') IS NULL
                        AND to_regprocedure('public.refresh_dashboard_ban_city_hourly_traffic()') IS NULL
                        AND NOT EXISTS (SELECT 1 FROM public.app_config_entries
                            WHERE config_key LIKE 'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic.%')
                        AND to_regclass('public.ban_history') IS NOT NULL
                        AND to_regclass('public.swarm_tracker') IS NOT NULL
                        AND to_regclass('public.user_lifetime_traffic') IS NOT NULL
                        AND to_regclass('public.user_career_daily') IS NOT NULL
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
    void upgradesAnAlreadyAppliedIncrementalV68WithoutRepair() throws Exception {
        Flyway.configure().dataSource(url, username, password).target("68")
                .locations("classpath:db/migration").load().migrate();
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT checksum FROM flyway_schema_history WHERE version = '68' AND success")) {
                assertTrue(result.next());
                assertEquals(-1348056337, result.getInt(1));
            }
            statement.execute("INSERT INTO dashboard_traffic_counts VALUES ('ban', NOW(), 'peer', 'fixture', 1)");
            statement.execute("INSERT INTO dashboard_traffic_swarm_changes VALUES (1, NOW())");
        }
        Flyway latest = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").validateOnMigrate(true).load();
        latest.migrate();
        latest.validate();
        assertEquals("79", latest.info().current().getVersion().getVersion());
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT NOT EXISTS (SELECT 1 FROM pg_class WHERE relnamespace = 'public'::regnamespace
                        AND relname LIKE 'dashboard_traffic_%')
                      AND NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'public.swarm_tracker'::regclass
                        AND tgname IN ('dashboard_swarm_insert_changes', 'dashboard_swarm_update_changes',
                                       'dashboard_swarm_remove_contributions'))
                      AND to_regprocedure('public.mark_dashboard_swarm_changes()') IS NULL
                      AND to_regprocedure('public.remove_dashboard_swarm_contributions()') IS NULL
                      AND EXISTS (SELECT 1 FROM pg_index
                        WHERE indexrelid = 'public.user_traffic_daily_day_idx'::regclass AND indisvalid)
                      AND EXISTS (SELECT 1 FROM app_config_entries
                        WHERE config_key = 'sparkle.homepage.network-traffic-enabled'
                          AND config_value = 'true' AND hot_reload)
                      AND (SELECT COUNT(*) = 2 FROM app_config_entries WHERE hot_reload AND
                        (config_key, config_value) IN (
                          ('sparkle.homepage.network-traffic-staff-only', 'false'),
                          ('sparkle.homepage.total-loss-staff-only', 'true')))
                      AND NOT EXISTS (SELECT 1 FROM app_config_entries
                        WHERE config_key LIKE 'sparkle.homepage-statistics-refresh.modules.bittorrent-traffic%')
                      AND (SELECT COUNT(*) = 3 FROM timescaledb_information.jobs
                        WHERE proc_name = 'policy_refresh_continuous_aggregate'
                          AND hypertable_schema = 'public' AND hypertable_name IN
                          ('dashboard_ban_daily_peer', 'dashboard_ban_daily_client', 'dashboard_ban_daily_geo'))
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
        }
    }

    @Test
    void adoptsTheCurrentProductionSchemaWithoutReplayingHistoricalMigrations() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
        Flyway.configure().dataSource(url, username, password).target("29")
                .locations("classpath:db/migration").load().migrate();

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
