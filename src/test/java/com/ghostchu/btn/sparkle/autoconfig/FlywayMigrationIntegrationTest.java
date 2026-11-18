package com.ghostchu.btn.sparkle.autoconfig;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.ghostchu.btn.sparkle.controller.admin.AdminBanSubmissionController;
import com.ghostchu.btn.sparkle.controller.admin.AdminContentModerationController;
import com.ghostchu.btn.sparkle.controller.admin.AdminUserApplicationController;
import com.ghostchu.btn.sparkle.controller.admin.AdminUserController;
import com.ghostchu.btn.sparkle.controller.handler.GlobalExceptionHandler;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnBan;
import com.ghostchu.btn.sparkle.controller.ui.HomepageIpFlagController;
import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.mapper.BanSubmissionGuardMapper;
import com.ghostchu.btn.sparkle.mapper.ContentModerationMapper;
import com.ghostchu.btn.sparkle.mapper.HomepageIpFlagMapper;
import com.ghostchu.btn.sparkle.mapper.SupporterRoleMapper;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import com.ghostchu.btn.sparkle.service.AdminSuspensionService;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.service.BtnBypassConflictAlertService;
import com.ghostchu.btn.sparkle.service.IRuleService;
import com.ghostchu.btn.sparkle.service.NotificationPreference;
import com.ghostchu.btn.sparkle.service.NotificationPreferenceService;
import com.ghostchu.btn.sparkle.service.RulePreviewService;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.UserPrivacySettings;
import com.ghostchu.btn.sparkle.util.SuspensionStatus;
import com.ghostchu.btn.sparkle.service.HomepageIpFlagService;
import com.ghostchu.btn.sparkle.service.HomepageStatisticsRefreshService;
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
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @SuppressWarnings("unchecked")
    void btnBypassConflictsRetryAtomicallyAndNotifyAgainAfterResolution() {
        Flyway.configure().dataSource(url, username, password).load().migrate();
        var dataSource = new DriverManagerDataSource(url, username, password);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO "user" (id, email, nickname, roles, avatar, register_at, last_login_at)
                VALUES (10, 'admin@example.invalid', 'Admin', ARRAY['user','admin'], '/avatar', now(), now()),
                       (11, 'mod@example.invalid', 'Moderator', ARRAY['user','moderator'], '/avatar', now(), now()),
                       (12, 'owner@example.invalid', 'Owner', ARRAY['user'], '/avatar', now(), now())
                """);
        var users = mock(IUserService.class);
        var owner = new User().setId(12L).setEmail("owner@example.invalid").setNickname("Owner");
        when(users.listByIds(List.of(10L, 11L, 12L))).thenReturn(List.of(
                new User().setId(10L).setRoles(new String[]{"user", "admin"}),
                new User().setId(11L).setRoles(new String[]{"user", "moderator"}),
                owner));
        when(users.listByIds(List.of(12L))).thenReturn(List.of(owner));
        var apps = mock(IUserappService.class);
        when(apps.listByIds(java.util.Set.of(21L))).thenReturn(List.of(
                new Userapp().setId(21L).setAppId("test-app").setOwner(12L).setBtnBypass(true)));
        var moduleConfig = mock(AnalyseModuleConfigService.class);
        when(moduleConfig.textValue("btnbypass-conflict-alert", "recipient-uids", "")).thenReturn("10,11,12");
        when(moduleConfig.isEnabled("btnbypass-userapps")).thenReturn(true);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> redisValues = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_OWNERS.getKey()))
                .thenReturn("{\"192.0.2.10\":[21]}");
        var manualRules = mock(IRuleService.class);
        var denyRule = new Rule().setCategory("test deny").setContent("192.0.2.0/24");
        when(manualRules.getRulesByType("ip_denylist")).thenReturn(List.of(denyRule));
        var preferences = mock(NotificationPreferenceService.class);
        var enabled = new NotificationPreference(NotificationCategory.ACCOUNT_SECURITY, true, true, true);
        when(preferences.preference(10L, NotificationCategory.ACCOUNT_SECURITY)).thenReturn(enabled);
        when(preferences.preference(11L, NotificationCategory.ACCOUNT_SECURITY))
                .thenThrow(new IllegalStateException("Simulated temporary delivery failure")).thenReturn(enabled);
        var mybatis = new MybatisConfiguration(new Environment("bypass", new SpringManagedTransactionFactory(), dataSource));
        new MyBatisPlusConfig().configurationCustomizer().customize(mybatis);
        mybatis.addMapper(UserMessageMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(mybatis));
        var messaging = new UserMessageService(session.getMapper(UserMessageMapper.class), users, null, preferences, null);
        var alerts = new BtnBypassConflictAlertService(moduleConfig, redis, new ObjectMapper(),
                new RulePreviewService(List.of(), List.of(), manualRules), apps, users, messaging, jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Runnable scan = () -> transaction.executeWithoutResult(status -> alerts.scan());

        assertThrows(IllegalStateException.class, scan::run);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_messages", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM btn_bypass_conflict_alerts", Integer.class));
        scan.run();
        scan.run();
        assertEquals(List.of(10L, 11L), jdbc.queryForList("""
                SELECT recipient_user_id FROM user_messages WHERE inbox_visible AND NOT email_requested
                ORDER BY recipient_user_id
                """, Long.class));
        String body = jdbc.queryForObject("SELECT content FROM user_messages WHERE recipient_user_id = 10", String.class);
        assertTrue(body.contains("test-app") && body.contains("ID：21") && body.contains("UID：12"));
        assertTrue(body.contains("Owner") && body.contains("owner@example.invalid") && body.contains("test deny"));
        when(manualRules.getRulesByType("ip_denylist")).thenReturn(List.of());
        scan.run();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM btn_bypass_conflict_alerts", Integer.class));
        when(manualRules.getRulesByType("ip_denylist")).thenReturn(List.of(denyRule));
        scan.run();
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM user_messages", Integer.class));
    }

    @Test
    void cleanupSkipsActiveWritersAndKeepsRefreshedRows() throws Exception {
        try (var connection = connect(url, username, password); var sql = connection.createStatement()) {
            for (String table : List.of("userapps_heartbeat", "swarm_tracker")) {
                String time = table.equals("swarm_tracker") ? "last_time_seen" : "last_seen_at";
                sql.execute("CREATE TABLE " + table + " (id bigint PRIMARY KEY, " + time + " timestamptz)");
                sql.execute("INSERT INTO " + table + " VALUES (1, now()-interval '10 days'), (2, now()-interval '10 days'), (3, now())");
            }
        }
        var configuration = new MybatisConfiguration(new Environment("cleanup", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        new MyBatisPlusConfig().configurationCustomizer().customize(configuration);
        configuration.addMapper(com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper.class);
        configuration.addMapper(com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var writer = connect(url, username, password); var sql = writer.createStatement();
             var cleaner = factory.openSession(true)) {
            writer.setAutoCommit(false);
            sql.execute("UPDATE userapps_heartbeat SET last_seen_at=now() WHERE id=1");
            sql.execute("UPDATE swarm_tracker SET last_time_seen=now() WHERE id=1");
            cleaner.getConnection().createStatement().execute("SET statement_timeout = '2s'");
            var cutoff = OffsetDateTime.now().minusDays(7);
            var heartbeats = cleaner.getMapper(com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper.class);
            var swarm = cleaner.getMapper(com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.class);
            assertEquals(1, heartbeats.deleteExpiredBatch(cutoff, 1000));
            assertEquals(1, swarm.deleteExpiredBatch(cutoff, 1000));
            writer.commit();
            assertEquals(0, heartbeats.deleteExpiredBatch(cutoff, 1000));
            assertEquals(0, swarm.deleteExpiredBatch(cutoff, 1000));
        }
    }

    @Test
    void profileUpdateLockDoesNotBlockForeignKeyChecks() throws Exception {
        try (var connection = connect(url, username, password); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE public.\"user\" (id bigint PRIMARY KEY, roles text[])");
            sql.execute("INSERT INTO public.\"user\" VALUES (1, ARRAY['user'])");
            sql.execute("CREATE TABLE test_child (user_id bigint REFERENCES public.\"user\"(id))");
        }
        var configuration = new MybatisConfiguration(new Environment("profile-lock", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.addMapper(UserMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertEquals(1L, session.getMapper(UserMapper.class).selectForUpdate(1).getId());
            executor.submit(() -> {
                try (var connection = connect(url, username, password); var sql = connection.createStatement()) {
                    sql.execute("SET statement_timeout = '2s'");
                    sql.execute("INSERT INTO test_child VALUES (1)");
                }
                return null;
            }).get(5, TimeUnit.SECONDS);
            session.rollback();
        }
    }

    @Test
    void atomicPointAdjustmentsSerializeAndRejectOutOfRangeBalances() throws Exception {
        try (var connection = connect(url, username, password); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE public.\"user\" (id bigint PRIMARY KEY, score_bytes bigint NOT NULL)");
            sql.execute("INSERT INTO public.\"user\" VALUES (1, 100), (2, 9223372036854775807)");
        }
        var configuration = new Configuration(new Environment("point-adjustments", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.addMapper(com.ghostchu.btn.sparkle.mapper.PointsMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CyclicBarrier(2);
            var futures = IntStream.range(0, 2).mapToObj(ignored -> executor.submit(() -> {
                try (var session = factory.openSession()) {
                    start.await(5, TimeUnit.SECONDS);
                    var result = session.getMapper(com.ghostchu.btn.sparkle.mapper.PointsMapper.class).adjustBalance(1, -80);
                    session.commit();
                    return result;
                }
            })).toList();
            int successful = 0;
            for (var future : futures) if (future.get(10, TimeUnit.SECONDS) != null) successful++;
            assertEquals(1, successful);
        }
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(com.ghostchu.btn.sparkle.mapper.PointsMapper.class);
            assertEquals(20L, mapper.balance(1));
            assertEquals(21L, mapper.adjustBalance(1, 1));
            assertEquals(22L, mapper.adjustBalance(1, 1));
            assertNull(mapper.adjustBalance(2, 1));
            assertNull(mapper.adjustBalance(1, Long.MIN_VALUE));
            assertNull(mapper.adjustBalance(999, 1));
            assertEquals(Long.MAX_VALUE, mapper.balance(2));
        }
    }

    @Test
    void timedSuspensionsPersistAndExpireInAdminRequests() throws Exception {
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE "user" (id bigint PRIMARY KEY, banned_at timestamptz, banned_reason text);
                    CREATE TABLE userapp (id bigint PRIMARY KEY, banned_at timestamptz, banned_reason text);
                    INSERT INTO "user" VALUES (1, now(), 'Existing reason');
                    INSERT INTO userapp VALUES (2, NULL, NULL);
                    """);
        }
        var flyway = Flyway.configure().dataSource(url, username, password).baselineVersion("80").target("81").load();
        flyway.baseline();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();

        var configuration = new MybatisConfiguration(new Environment("suspension-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(UserappMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
             var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            var userMapper = session.getMapper(UserMapper.class);
            var appMapper = session.getMapper(UserappMapper.class);
            var service = new AdminSuspensionService(userMapper, appMapper);
            var users = mock(IUserService.class);
            var apps = mock(IUserappService.class);
            var privacy = mock(UserPrivacyService.class);
            var user = new User().setId(1L).setRole("user");
            var app = new Userapp().setId(2L).setOwner(1L);
            when(users.getById(1L)).thenReturn(user);
            when(apps.getById(2L)).thenReturn(app);
            when(privacy.get(1L)).thenReturn(UserPrivacySettings.defaults(1L));
            var mvc = MockMvcBuilders.standaloneSetup(
                            new AdminUserController(users, null, service, apps, null, null, privacy),
                            new AdminUserApplicationController(apps, users, service))
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
            assertEquals(1, userMapper.selectCount(new QueryWrapper<User>().apply(SuspensionStatus.SUSPENDED_SQL)));
            var until = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(1).withNano(0);
            var body = """
                    {"banned":true,"reason":"  Duplicate reports  ","unbanAt":"%s"}
                    """.formatted(until);
            mvc.perform(post("/api/admin/users/1/ban").contentType("application/json")
                            .content("""
                                    {"banned":true,"reason":"   "}
                                    """))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/admin/users/1/ban").contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.bannedReason").value("Duplicate reports"));
            mvc.perform(post("/api/admin/userapps/2/ban").contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.bannedReason").value("Duplicate reports"));
            try (var rows = statement.executeQuery("""
                    SELECT banned_reason, unban_at FROM "user"
                    UNION ALL SELECT banned_reason, unban_at FROM userapp
                    """)) {
                for (int i = 0; i < 2; i++) {
                    assertTrue(rows.next());
                    assertEquals("Duplicate reports", rows.getString(1));
                    assertTrue(until.isEqual(rows.getObject(2, OffsetDateTime.class)));
                }
                assertFalse(rows.next());
            }
            assertTrue(user.isSuspended());
            assertTrue(app.isSuspended());
            assertEquals(1, appMapper.selectCount(new QueryWrapper<Userapp>().apply(SuspensionStatus.SUSPENDED_SQL)));
            statement.execute("UPDATE userapp SET unban_at = CURRENT_TIMESTAMP - interval '1 second'");
            assertEquals(1, appMapper.selectCount(new QueryWrapper<Userapp>().apply(SuspensionStatus.NOT_SUSPENDED_SQL)));
            mvc.perform(post("/api/admin/users/1/ban").contentType("application/json")
                            .content("""
                                    {"banned":false}
                                    """))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.bannedAt").doesNotExist());
            try (var rows = statement.executeQuery("""
                    SELECT banned_at, banned_reason, unban_at FROM "user" WHERE id = 1
                    """)) {
                assertTrue(rows.next());
                for (int column = 1; column <= 3; column++) assertNull(rows.getObject(column));
            }
        }
    }

    @Test
    void dailyIpFlagsPublishOnlyNewBitsAndKeepResultsAfterPruning() throws Exception {
        var day = LocalDate.now(ZoneOffset.UTC).minusDays(3);
        var ip = InetAddress.ofLiteral("2001:db8::1");
        var other = InetAddress.ofLiteral("192.0.2.4");
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");
            statement.execute("CREATE TABLE swarm_tracker (last_time_seen timestamptz, peer_ip inet, flags text)");
            statement.execute("""
                    CREATE TABLE app_config_entries (
                        config_key text PRIMARY KEY, config_value text, value_type text, config_group text,
                        source text, sensitive boolean, hot_reload boolean
                    )
                    """);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO swarm_tracker VALUES (?, '2001:db8::1', 'd H'), (?, '2001:db8::1', 'X e'),
                                                     (?, '2001:db8::1', 'd H')
                    """)) {
                insert.setObject(1, day.atTime(23, 59).atOffset(ZoneOffset.UTC));
                insert.setObject(2, day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
                insert.setObject(3, day.atStartOfDay().atOffset(ZoneOffset.UTC));
                insert.executeUpdate();
            }
        }
        var flyway = Flyway.configure().dataSource(url, username, password)
                .baselineVersion("79").target("80").load();
        flyway.baseline();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();

        var configuration = new Configuration(new Environment("ip-flag-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.addMapper(HomepageIpFlagMapper.class);
        try (var input = getClass().getResourceAsStream("/mapper/HomepageIpFlagMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/HomepageIpFlagMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(HomepageIpFlagMapper.class);
            var service = new HomepageIpFlagService(mapper);
            var time = day.atTime(23, 30).atOffset(ZoneOffset.UTC);
            var seeded = service.daily(day, day.plusDays(1));
            assertEquals(java.util.Map.of("total", 1L, "dht", 1L, "peerChoked", 1L, "tcp", 1L),
                    seeded.getFirst().counts());
            assertEquals(java.util.Map.of("total", 1L, "pex", 1L, "plaintext", 1L, "tcp", 1L),
                    seeded.getLast().counts());
            service.observe(List.of(swarmSample(time, ip, "d H"), swarmSample(time, other, null),
                    swarmSample(time.plusDays(1), ip, "X e")));
            assertTrue(mapper.hasOlderPending(day.plusDays(1)));
            assertEquals(1, mapper.publish(1, 17));
            assertEquals(0, mapper.publish(100, 17));
            assertEquals(2L, service.daily(day, day).getFirst().counts().get("total"));

            var late = swarmSample(time.withOffsetSameInstant(ZoneOffset.ofHours(8)), ip, "D H P");
            service.observe(List.of(late, late));
            assertEquals(1, mapper.publish(100, 17));
            service.observe(List.of(late));
            assertEquals(0, mapper.publish(100, 17));
            var days = service.daily(day, day.plusDays(1));
            assertEquals(java.util.Map.of("total", 2L, "dht", 1L, "peerChoked", 1L,
                    "peerUnchoked", 1L, "tcp", 1L, "utp", 1L), days.getFirst().counts());
            assertEquals(java.util.Map.of("total", 1L, "pex", 1L, "plaintext", 1L, "tcp", 1L),
                    days.getLast().counts());
            assertFalse(mapper.hasOlderPending(day.plusDays(1)));
            assertEquals(1, mapper.prune(day.plusDays(1)).size());
            assertEquals(days, service.daily(day, day.plusDays(1)));

            var refreshService = mock(HomepageStatisticsRefreshService.class);
            when(refreshService.isIpFlagsEnabled()).thenReturn(true);
            var mvc = MockMvcBuilders.standaloneSetup(new HomepageIpFlagController(service, refreshService)).build();
            mvc.perform(get("/api/ui/home/ip-flags").param("from", day.toString()).param("to", day.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].day").value(day.toString()))
                    .andExpect(jsonPath("$[0].counts.total").value(2));
            mvc.perform(get("/api/ui/home/ip-flags").param("from", day.minusDays(90).toString())
                            .param("to", day.toString()))
                    .andExpect(status().isBadRequest());
            when(refreshService.isIpFlagsEnabled()).thenReturn(false);
            mvc.perform(get("/api/ui/home/ip-flags").param("from", day.toString()).param("to", day.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    private static SwarmTracker swarmSample(OffsetDateTime time, InetAddress ip, String flags) {
        return new SwarmTracker().setLastTimeSeen(time).setPeerIp(ip).setFlags(flags);
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
                        privacy_level
                    )
                    VALUES (
                        '/img/anonymous-user.png',
                        'points-migration-test@example.invalid',
                        'points-migration-test',
                        NOW(), NOW(), 2
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
    void queuesAuditsAndStreamsUserExports() throws Exception {
        Flyway.configure().dataSource(url, username, password).load().migrate();
        try (var connection = connect(url, username, password); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO userapp (id, app_id, app_secret, created_at, owner, comment)
                    VALUES (101, 'a', 'fixture', now(), 10, 'Own application'),
                           (102, 'b', 'fixture', now(), 20, 'Other application');
                    INSERT INTO ban_history (insert_time, populate_time, userapps_id, torrent_id, peer_ip,
                        peer_port, peer_progress, to_peer_traffic, from_peer_traffic, module_name, rule, structured_data)
                    VALUES (now(), now(), 101, 1, '192.0.2.1', 123, 1, 0, 0, 'Test', 'own', '{"port":123}'),
                           (now(), now(), 102, 1, '192.0.2.2', 456, 1, 0, 0, 'Test', 'other', '{}');
                    INSERT INTO client_discovery (hash, found_at, found_userapps_id, peer_id, peer_client_name)
                    VALUES (1, now(), 101, 'P1', 'Client One');
                    INSERT INTO swarm_tracker (userapps_id, user_downloader, torrent_id, peer_ip, peer_port,
                        peer_progress, from_peer_traffic, to_peer_traffic, from_peer_traffic_offset,
                        to_peer_traffic_offset, first_time_seen, last_time_seen, user_progress)
                    VALUES (101, 'fixture', 1, '192.0.2.3', 123, 1, 0, 0, 0, 0, now(), now(), 1);
                    INSERT INTO user_traffic_daily (user_id, day, upload) VALUES
                        (10, current_date, 9007199254740993), (20, current_date, 10);
                    """);
        }
        var configuration = new MybatisConfiguration(new Environment("data-export-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.getTypeHandlerRegistry().register(com.ghostchu.btn.sparkle.converter.UuidTypeHandler.class);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        configuration.addInterceptor(interceptor);
        for (String file : List.of("DataExportJobMapper", "ExportQueryMapper", "SubmissionMapper",
                "UserTrafficStatisticsMapper")) {
            String path = "mapper/" + file + ".xml";
            try (var input = getClass().getResourceAsStream("/" + path)) {
                new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
            }
        }
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var id = UUID.randomUUID();
        var category = com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Category.BANS;
        var job = new com.ghostchu.btn.sparkle.service.export.DataExportJob(id, 10, category, "{}", "zh", now,
                100, 200, 72, "QUEUED", null, null, null);
        try (var session = factory.openSession(false)) {
            var jobs = session.getMapper(com.ghostchu.btn.sparkle.mapper.DataExportJobMapper.class);
            assertEquals("sparkle.exports.pool-mib", jobs.lockAdmission());
            assertEquals(1, jobs.enqueue(job, now.toLocalDate(), "192.0.2.10", "Test Client"));
            assertEquals(1L, jobs.usedSlots(10, now.toLocalDate()));
            assertEquals(200L, jobs.reservedBytes());
            var claimed = jobs.claim();
            assertEquals(id, claimed.id());
            assertEquals(category, claimed.category());
            assertEquals("RUNNING", claimed.status());
            assertNull(jobs.claim());
            assertEquals(1, jobs.renew(id));
            assertNull(jobs.find(id, 20));
            assertEquals(1, jobs.ready(id, 1, 123, false, "fixture", now.minusSeconds(1)));
            assertEquals(123L, jobs.reservedBytes());
            assertEquals(id, jobs.removable().getFirst().id());
            assertEquals(1, jobs.removed(id));
            assertEquals("EXPIRED", jobs.find(id, 10).status());
            assertEquals(1L, jobs.usedSlots(10, now.toLocalDate()));
            assertEquals(0L, jobs.reservedBytes());
            var failed = new com.ghostchu.btn.sparkle.service.export.DataExportJob(UUID.randomUUID(), 10, category,
                    "{}", "zh", now, 100, 200, 72, "QUEUED", null, null, null);
            assertEquals(1, jobs.enqueue(failed, now.toLocalDate(), "192.0.2.10", "Test Client"));
            assertEquals(failed.id(), jobs.claim().id());
            assertEquals(1, jobs.fail(failed.id(), false));
            assertEquals(0, jobs.fail(failed.id(), false));
            assertEquals(1L, jobs.usedSlots(10, now.toLocalDate()));
            session.commit();

            var export = session.getMapper(com.ghostchu.btn.sparkle.mapper.ExportQueryMapper.class);
            var bans = new QueryWrapper<>().eq("rule", "own").orderByDesc("populate_time");
            try (var cursor = export.bans(bans, 10)) {
                var rows = cursor.iterator();
                var row = rows.next();
                assertTrue(row.get("structured_data").toString().contains("123"));
                assertFalse(row.containsKey("userapps_id"));
                assertFalse(rows.hasNext());
            }
            try (var cursor = export.clients(new QueryWrapper<>().orderByAsc("found_at"), 10)) {
                assertEquals("Client One", cursor.iterator().next().get("peer_client_name"));
            }
            try (var cursor = export.swarm(new QueryWrapper<>().orderByAsc("last_time_seen"), 10)) {
                assertEquals(123, cursor.iterator().next().get("peer_port"));
            }
            var submissions = session.getMapper(com.ghostchu.btn.sparkle.mapper.SubmissionMapper.class);
            for (String type : List.of("bans", "clients", "swarm")) {
                try (var cursor = submissions.exportRows(10, List.of(101L, 102L), type, now.minusDays(1), now, 10)) {
                    var rows = cursor.iterator();
                    assertEquals("Own application", rows.next().get("application"));
                    assertFalse(rows.hasNext());
                }
            }
            var traffic = session.getMapper(com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper.class);
            try (var cursor = traffic.exportDaily(10, 10)) {
                var rows = cursor.iterator();
                assertEquals(new BigDecimal("9007199254740993"), rows.next().get("upload"));
                assertFalse(rows.hasNext());
            }
        }
    }

    @Test
    void preservesLegacyRolesAndGrantsSupporterOnceAcrossPlatforms() throws Exception {
        Flyway.configure().dataSource(url, username, password).target("81").load().migrate();
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO "user" (id, email, nickname, role, avatar, register_at, last_login_at)
                    VALUES (101, 'admin@example.invalid', 'Admin', 'ROLE_ADMIN', '/img/anonymous-user.png', now(), now()),
                           (102, 'moderator@example.invalid', 'Moderator', 'moderator', '/img/anonymous-user.png', now(), now())
                    """);
        }
        Flyway.configure().dataSource(url, username, password).load().migrate();
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO member_platform_bindings (provider, btn_user_id, external_user_id, proof)
                    VALUES ('AFDIAN', 101, 'afdian-1', 'a'), ('MIANBAODUO', 101, 'mianbao-1', 'm'),
                           ('PATREON', 101, 'patron-1', 'p'), ('AFDIAN', 102, 'afdian-2', 'b');
                    INSERT INTO currency_exchange_rates (base_currency, quote_currency, rate_to_base)
                    VALUES ('EUR', 'USD', 0.8), ('EUR', 'CNY', 0.1);
                    INSERT INTO member_transactions (provider, external_transaction_id, external_user_id,
                        transaction_type, amount, currency, amount_cny, amount_base, base_currency,
                        transaction_status, remote_present, license_consumed)
                    VALUES ('AFDIAN', 'a1', 'afdian-1', 'DONATION', 30, 'CNY', 25, 30, 'CNY', '2', TRUE, TRUE),
                           ('MIANBAODUO', 'm1', 'mianbao-1', 'PRODUCT', 30, 'CNY', 30, 30, 'CNY', 'PAID', TRUE, TRUE),
                           ('PATREON', 'p1', 'patron-1', 'DONATION', 5, 'USD', 4, 4, 'EUR', 'ACTIVE', TRUE, FALSE),
                           ('AFDIAN', 'a2', 'afdian-1', 'DONATION', 1000, 'CNY', 1000, 1000, 'CNY', 'REFUNDED', TRUE, FALSE),
                           ('AFDIAN', 'a3', 'afdian-1', 'DONATION', 1000, 'CNY', 1000, 1000, 'CNY', '2', FALSE, FALSE),
                           ('AFDIAN', 'a4', 'unbound', 'DONATION', 1000, 'CNY', 1000, 1000, 'CNY', '2', TRUE, FALSE),
                           ('AFDIAN', 'a5', 'afdian-2', 'DONATION', 200, 'CNY', 200, 200, 'CNY', '2', TRUE, FALSE);
                    """);
        }

        var configuration = new MybatisConfiguration(new Environment("supporter-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
        configuration.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        configuration.addInterceptor(interceptor);
        for (String mapper : List.of("mapper/UserMapper.xml", "mapper/SupporterRoleMapper.xml")) {
            try (var input = getClass().getResourceAsStream("/" + mapper)) {
                new XMLMapperBuilder(input, configuration, mapper, configuration.getSqlFragments()).parse();
            }
        }
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession(true)) {
            var users = session.getMapper(UserMapper.class);
            var supporters = session.getMapper(SupporterRoleMapper.class);
            assertEquals(List.of("user", "admin"), users.selectById(101L).roleNames());
            assertEquals(List.of("user", "moderator"), users.selectForUpdate(102L).roleNames());
            assertEquals(101L, supporters.findBoundUser("AFDIAN", "afdian-1"));
            assertEquals(List.of(101L, 102L), supporters.findCandidates(0, 200));
            assertTrue(supporters.needsCnyRates());
            assertEquals(0, supporters.grantEligible(List.of(101L), new BigDecimal("100.01")));

            users.replaceRoles(102L, new String[]{"user", "moderator", "supporter"});
            users.replaceRoles(102L, new String[]{"user", "moderator"});
        }

        Callable<Integer> grant = () -> {
            try (var session = factory.openSession(true)) {
                return session.getMapper(SupporterRoleMapper.class)
                        .grantEligible(List.of(101L), new BigDecimal("100.00"));
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(List.of(grant, grant));
            assertEquals(1, results.get(0).get() + results.get(1).get());
        }
        try (var session = factory.openSession(true)) {
            var users = session.getMapper(UserMapper.class);
            var supporters = session.getMapper(SupporterRoleMapper.class);
            assertEquals(List.of("user", "admin", "supporter"), users.selectById(101L).roleNames());
            users.replaceRoles(101L, new String[]{"user", "admin"});
            assertTrue(supporters.findCandidates(0, 200).isEmpty());
            assertEquals(0, supporters.grantEligible(List.of(101L, 102L), BigDecimal.ONE));
            assertEquals(List.of("user", "admin"), users.selectForUpdate(101L).roleNames());
            assertEquals(List.of("user", "moderator"), users.selectForUpdate(102L).roleNames());
            users.clearRolesForDeletedUser(102L);
            assertEquals(List.of("user"), users.selectById(102L).roleNames());
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
            statement.execute("""
                    INSERT INTO user_career_events (user_id, kind, fingerprint, observed_at, reporter_id)
                    VALUES (11, 'observation', '00000000-0000-0000-0000-000000000001', '2026-01-01T00:00:00Z', 22);
                    INSERT INTO user_career_daily (user_id, day, kind, amount)
                    VALUES (11, '2026-01-01', 'observation', 1);
                    """);
        }
        Flyway latest = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").validateOnMigrate(true).load();
        latest.migrate();
        latest.validate();
        assertEquals("85", latest.info().current().getVersion().getVersion());
        try (var connection = connect(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'user_career_events' AND column_name = 'reporter_id')
                      AND to_regclass('public.user_career_latest_observation') IS NULL
                      AND EXISTS (SELECT 1 FROM user_career_events WHERE user_id = 11 AND kind = 'observation'
                        AND fingerprint = '00000000-0000-0000-0000-000000000001')
                      AND EXISTS (SELECT 1 FROM user_career_daily WHERE user_id = 11 AND kind = 'observation'
                        AND day = '2026-01-01' AND amount = 1)
                    """)) {
                assertTrue(result.next());
                assertTrue(result.getBoolean(1));
            }
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
