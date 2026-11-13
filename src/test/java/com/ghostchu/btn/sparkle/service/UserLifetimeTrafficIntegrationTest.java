package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_ALLOW_DESTRUCTIVE", matches = "true")
class UserLifetimeTrafficIntegrationTest {
    @Test
    void backfillsAndAccumulatesWithoutCountingRetriesOrLosingRetainedTotals() throws Exception {
        String source = System.getenv("SPARKLE_FLYWAY_TEST_URL");
        String username = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_USER", "postgres");
        String password = System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_PASSWORD", "");
        String database = "sparkle_lifetime_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(source, username, password);
             var statement = admin.createStatement()) {
            try (var result = statement.executeQuery("SELECT current_database()")) {
                assertTrue(result.next());
                assertTrue(result.getString(1).toLowerCase().contains("test"));
            }
            statement.execute("CREATE DATABASE " + database);
            String[] parts = source.split("\\?", 2);
            String url = parts[0].substring(0, parts[0].lastIndexOf('/') + 1) + database
                    + (parts.length == 2 ? "?" + parts[1] : "");
            try {
                Flyway.configure().dataSource(url, username, password).target("60").load().migrate();
                try (var connection = DriverManager.getConnection(url, username, password);
                     var sql = connection.createStatement()) {
                    sql.execute("""
                            INSERT INTO "user" (id, avatar, email, nickname, register_at, last_login_at)
                            SELECT id, '/test.png', id || '@example.invalid', 'Test',
                                   '2026-01-01Z'::timestamptz, now() FROM generate_series(1, 2) id;
                            INSERT INTO userapp (id, app_id, app_secret, created_at, owner, comment)
                            VALUES (1, 'test1', 'test-only', now(), 1, ''),
                                   (2, 'test2', 'test-only', now(), 2, ''),
                                   (3, 'test3', 'test-only', now(), 2, '');
                            INSERT INTO userapps_heartbeat (userapp_id, ip, first_seen_at, last_seen_at)
                            VALUES (2, '192.0.2.1', '2026-01-01Z', '2026-12-01Z'),
                                   (3, '192.0.2.1', '2026-01-01Z', '2026-12-01Z');
                            """);
                    sql.execute(trackerInsert(100, 40, "2026-11-01Z"));
                }
                Flyway latest = Flyway.configure().dataSource(url, username, password).load();
                latest.migrate();
                latest.validate();
                try (var connection = DriverManager.getConnection(url, username, password);
                     var sql = connection.createStatement()) {
                    assertTotals(connection, 1, "100", "40", "0", "0");
                    assertTotals(connection, 2, "0", "0", "40", "100");
                    try (var result = sql.executeQuery("SELECT bool_and(history_partial) FROM user_lifetime_traffic")) {
                        assertTrue(result.next());
                        assertTrue(result.getBoolean(1));
                    }
                    // Retry, increase, out-of-order decrease, retry after decrease.
                    for (int bytes : new int[]{100, 150, 80, 150}) {
                        sql.execute("UPDATE swarm_tracker SET to_peer_traffic = " + bytes);
                    }
                    assertTotals(connection, 1, "150", "40", "0", "0");
                    assertTotals(connection, 2, "0", "0", "40", "150");
                    // Raw cleanup and subsequent replay must retain high-water marks.
                    sql.execute("DELETE FROM swarm_tracker");
                    sql.execute(trackerInsert(150, 40, "2026-11-01Z"));
                    assertTotals(connection, 1, "150", "40", "0", "0");
                    // A new client session starts a new counter epoch.
                    sql.execute("UPDATE swarm_tracker SET first_time_seen = '2026-11-02Z', to_peer_traffic = 20, from_peer_traffic = 10");
                    assertTotals(connection, 1, "170", "50", "0", "0");
                    assertTotals(connection, 2, "0", "0", "50", "170");
                    connection.setAutoCommit(false);
                    sql.execute("UPDATE swarm_tracker SET to_peer_traffic = 999");
                    connection.rollback();
                    connection.setAutoCommit(true);
                    assertTotals(connection, 1, "170", "50", "0", "0");
                    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                        var updates = java.util.stream.IntStream.of(200, 100, 200).mapToObj(bytes ->
                                executor.submit(() -> {
                                    try (var concurrent = DriverManager.getConnection(url, username, password);
                                         var update = concurrent.createStatement()) {
                                        update.execute("UPDATE swarm_tracker SET to_peer_traffic = " + bytes);
                                    }
                                    return true;
                                })).toList();
                        for (var update : updates) assertTrue(update.get());
                    }
                    assertTotals(connection, 1, "350", "50", "0", "0");
                    assertTotals(connection, 2, "0", "0", "50", "350");
                    sql.execute("DELETE FROM userapp WHERE id = 1");
                    assertTotals(connection, 1, "350", "50", "0", "0");
                    sql.execute("""
                            INSERT INTO "user" (id, avatar, email, nickname, register_at, last_login_at)
                            VALUES (3, '/test.png', '3@example.invalid', 'Test', now(), now());
                            """);
                    try (var result = sql.executeQuery("SELECT history_partial FROM user_lifetime_traffic WHERE user_id = 3")) {
                        assertTrue(result.next());
                        assertFalse(result.getBoolean(1));
                    }
                    sql.execute("INSERT INTO user_privacy_settings (user_id) VALUES (3)");
                    try (var result = sql.executeQuery("SELECT lifetime_traffic_visibility FROM user_privacy_settings WHERE user_id = 3")) {
                        assertTrue(result.next());
                        assertEquals("HIDDEN", result.getString(1));
                    }
                    sql.execute("UPDATE user_lifetime_traffic SET sent_self = 9223372036854775808 WHERE user_id = 3");
                }
                // Verify production MyBatis record mapping and exact decimal strings above Long.MAX_VALUE.
                Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                        new UnpooledDataSource("org.postgresql.Driver", url, username, password)));
                config.setMapUnderscoreToCamelCase(true);
                config.addMapper(UserLifetimeTrafficMapper.class);
                try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                    var traffic = session.getMapper(UserLifetimeTrafficMapper.class).selectByUserId(3);
                    assertEquals("9223372036854775808", traffic.sentTrafficSelfReport());
                    assertNotNull(traffic.trackedSince());
                    assertFalse(traffic.historyPartial());
                }
            } finally {
                statement.execute("DROP DATABASE " + database + " WITH (FORCE)");
            }
        }
    }

    private String trackerInsert(long upload, long download, String firstSeen) {
        return """
                INSERT INTO swarm_tracker (userapps_id, user_downloader, torrent_id, peer_ip,
                    peer_port, peer_progress, from_peer_traffic, to_peer_traffic,
                    from_peer_traffic_offset, to_peer_traffic_offset, flags,
                    first_time_seen, last_time_seen, user_progress)
                VALUES (1, 'test', 1, '192.0.2.1', 6881, 0, %d, %d, 0, 0, '', '%s', '2026-11-03Z', 0)
                """.formatted(download, upload, firstSeen);
    }

    private void assertTotals(Connection connection, long userId, String... expected) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT sent_self, received_self, sent_ack, received_ack"
                     + " FROM user_lifetime_traffic WHERE user_id = " + userId)) {
            assertTrue(result.next());
            for (int i = 0; i < expected.length; i++) assertEquals(expected[i], result.getString(i + 1));
        }
    }
}
