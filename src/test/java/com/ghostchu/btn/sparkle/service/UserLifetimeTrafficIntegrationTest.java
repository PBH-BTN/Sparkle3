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
    void skipsHistoricalRowsAndAccumulatesTransactionallyWithoutDatabaseFunctions() throws Exception {
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
                var dataSource = new UnpooledDataSource("org.postgresql.Driver", url, username, password);
                var config = new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
                config.setMapUnderscoreToCamelCase(true);
                config.addMapper(com.ghostchu.btn.sparkle.mapper.CareerIngestionMapper.class);
                config.addMapper(UserLifetimeTrafficMapper.class);
                config.addMapper(com.ghostchu.btn.sparkle.mapper.UserCareerMapper.class);
                try (var resource = getClass().getResourceAsStream("/mapper/CareerIngestionMapper.xml")) {
                    new org.apache.ibatis.builder.xml.XMLMapperBuilder(resource,config,
                            "mapper/CareerIngestionMapper.xml",config.getSqlFragments()).parse();
                }
                var factory = new SqlSessionFactoryBuilder().build(config);
                try (var session = factory.openSession()) {
                    var connection=session.getConnection();
                    var totals=session.getMapper(UserLifetimeTrafficMapper.class);
                    var career=session.getMapper(com.ghostchu.btn.sparkle.mapper.UserCareerMapper.class);
                    var ingestion=new CareerIngestionService(session.getMapper(com.ghostchu.btn.sparkle.mapper.CareerIngestionMapper.class));
                    assertTotals(connection,1,"0","0","0","0");
                    assertTrue(career.counts(1).isEmpty());
                    try(var sql=connection.createStatement(); var result=sql.executeQuery(
                            "SELECT COUNT(*) FROM pg_proc WHERE proname IN ('accumulate_user_lifetime_traffic','career_source_event','career_count_event','initialize_user_lifetime_traffic')")) {
                        assertTrue(result.next()); assertEquals(0,result.getLong(1));
                    }
                    var at=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
                    var row=new com.ghostchu.btn.sparkle.entity.SwarmTracker()
                            .setUserappsId(1L).setUserDownloader("test").setTorrentId(1L)
                            .setPeerIp(java.net.InetAddress.ofLiteral("192.0.2.1")).setPeerPort(6881)
                            .setFirstTimeSeen(at.minusDays(1)).setLastTimeSeen(at)
                            .setToPeerTraffic(1000L).setFromPeerTraffic(500L)
                            .setToPeerTrafficOffset(10L).setFromPeerTrafficOffset(5L).setUserProgress(1);
                    ingestion.swarm(1,row);
                    assertTotals(connection,1,"10","5","0","0");
                    assertTotals(connection,2,"0","0","5","10");
                    ingestion.swarm(1,row);
                    assertTotals(connection,1,"10","5","0","0");
                    row.setToPeerTraffic(1020L).setLastTimeSeen(at.plusSeconds(60));
                    ingestion.swarm(1,row);
                    assertTotals(connection,1,"30","5","0","0");
                    assertEquals("60.000000",career.durations(1).get("seeding"));
                    row.setToPeerTraffic(800L);
                    ingestion.swarm(1,row);
                    assertTotals(connection,1,"30","5","0","0");
                    ingestion.featureByApp(1,991,at);
                    ingestion.featureByApp(1,991,at);
                    assertEquals("1",career.counts(1).stream().filter(r -> "feature".equals(r.get("kind"))).findFirst().orElseThrow().get("total"));
                    session.commit();
                    row.setToPeerTraffic(2000L);
                    ingestion.swarm(1,row);
                    session.rollback();
                    assertTotals(connection,1,"30","5","0","0");
                    assertEquals("30",totals.selectByUserId(1).sentTrafficSelfReport());
                    row.setToPeerTraffic(1020L);
                    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                        var futures = java.util.stream.IntStream.range(0,3).mapToObj(i -> executor.submit(() -> {
                            try (var concurrent = factory.openSession()) {
                                new CareerIngestionService(concurrent.getMapper(com.ghostchu.btn.sparkle.mapper.CareerIngestionMapper.class)).swarm(1,row);
                                concurrent.commit();
                            }
                            return true;
                        })).toList();
                        for (var future : futures) assertTrue(future.get());
                    }
                    assertTotals(connection,1,"30","5","0","0");
                    try (var sql=connection.createStatement()) {
                        sql.execute("DELETE FROM swarm_tracker");
                        sql.execute("UPDATE user_lifetime_traffic SET sent_self=9223372036854775808 WHERE user_id=1");
                    }
                    ingestion.swarm(1,row);
                    session.clearCache();
                    assertEquals("9223372036854775808",totals.selectByUserId(1).sentTrafficSelfReport());
                    var ban = new com.ghostchu.btn.sparkle.entity.BanHistory().setUserappsId(1L).setTorrentId(1L)
                            .setPeerIp(java.net.InetAddress.ofLiteral("192.0.2.1")).setPeerPort(6881)
                            .setPopulateTime(at).setModuleName("test").setRule("test");
                    ingestion.ban(1,ban);
                    ingestion.ban(1,ban);
                    assertEquals("1",career.counts(1).stream().filter(r -> "ban".equals(r.get("kind"))).findFirst().orElseThrow().get("total"));
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
