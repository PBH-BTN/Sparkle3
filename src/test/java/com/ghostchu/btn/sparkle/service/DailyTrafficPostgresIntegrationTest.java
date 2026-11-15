package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.NetworkTrafficAccess;
import com.ghostchu.btn.sparkle.mapper.CareerBatchMapper;
import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SPARKLE_FLYWAY_TEST_URL",matches=".+")
class DailyTrafficPostgresIntegrationTest {
    @Test
    void flywayAppliesV70AndStoresExactFinalRatios() throws Exception {
        String url=System.getenv("SPARKLE_FLYWAY_TEST_URL");
        String user=System.getenv("SPARKLE_FLYWAY_TEST_USER");
        String password=System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD");
        String schema="daily_traffic_test_"+UUID.randomUUID().toString().replace("-", "");
        try (var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            statement.execute("CREATE SCHEMA "+schema);
            try {
                var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,user,password)
                        .schemas(schema).defaultSchema(schema).baselineVersion("69").target("70").load();
                flyway.baseline();
                assertEquals(1,flyway.migrate().migrationsExecuted);
                flyway.validate();
                statement.execute("INSERT INTO "+schema+".user_traffic_daily(user_id,day,upload,loss) VALUES(1,'2020-01-01',9007199254740993,9007199254740993)");
                try (var result=statement.executeQuery("SELECT upload,loss_ratio FROM "+schema+".user_traffic_daily")) {
                    assertTrue(result.next());
                    assertEquals("9007199254740993",result.getString(1));
                    assertEquals("100.00",result.getString(2));
                }
            } finally { statement.execute("DROP SCHEMA "+schema+" CASCADE"); }
        }
    }

    @Test
    void finalResultsDeduplicateReportsAndSplitActivityAtUtcMidnight() throws Exception {
        try (var connection=DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"),System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement=connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE userapp(id bigint,owner bigint)");
                statement.execute("CREATE TEMP TABLE userapps_heartbeat(userapp_id bigint,ip inet,first_seen_at timestamptz,last_seen_at timestamptz)");
                statement.execute("CREATE TEMP TABLE user_account_deletion_request(user_id bigint,status text)");
                statement.execute("CREATE TEMP TABLE user_career_events(user_id bigint,kind text,fingerprint uuid,observed_at timestamptz,reporter_id bigint,PRIMARY KEY(user_id,kind,fingerprint))");
                statement.execute("CREATE TEMP TABLE user_career_daily(user_id bigint,day date,kind text,amount bigint,PRIMARY KEY(user_id,kind,day))");
                statement.execute("CREATE TEMP TABLE user_career_torrent_time(user_id bigint,torrent_id bigint,last_seen timestamptz,was_seeding boolean,seeding tstzmultirange DEFAULT '{}',downloading tstzmultirange DEFAULT '{}',PRIMARY KEY(user_id,torrent_id))");
                statement.execute("CREATE TEMP TABLE user_lifetime_traffic_checkpoint(user_id bigint,session_key uuid,uploaded bigint,downloaded bigint,upload_delta bigint,download_delta bigint,PRIMARY KEY(user_id,session_key))");
                statement.execute("CREATE TEMP TABLE user_lifetime_traffic(user_id bigint PRIMARY KEY,sent_self numeric,received_self numeric,sent_ack numeric,received_ack numeric,tracked_since timestamptz,history_partial boolean,updated_at timestamptz)");
                try (var input=getClass().getResourceAsStream("/db/migration/V70__user_daily_traffic_statistics.sql")) {
                    statement.execute(new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("CREATE TABLE","CREATE TEMP TABLE"));
                }
                statement.execute("INSERT INTO userapp VALUES(2,2)");
                statement.execute("INSERT INTO userapps_heartbeat VALUES(2,'192.0.2.1','2026-01-01 00:00Z','2026-01-02 00:05Z')");
            }
            var config=new Configuration();
            try (var input=getClass().getResourceAsStream("/mapper/CareerBatchMapper.xml")) {
                new XMLMapperBuilder(input,config,"CareerBatchMapper.xml",config.getSqlFragments()).parse();
            }
            try (var input = getClass().getResourceAsStream("/mapper/UserTrafficStatisticsMapper.xml")) {
                new XMLMapperBuilder(input, config, "UserTrafficStatisticsMapper.xml", config.getSqlFragments()).parse();
            }
            try (var session=new SqlSessionFactoryBuilder().build(config).openSession(connection)) {
                var mapper=session.getMapper(CareerBatchMapper.class);
                var row=new HashMap<String,Object>();
                row.put("key",UUID.randomUUID().toString()); row.put("observationKey",UUID.randomUUID().toString());
                row.put("upload",100L); row.put("download",40L);
                row.put("initialUpload",100L); row.put("initialDownload",40L);
                row.put("at",OffsetDateTime.parse("2026-01-01T23:59:30Z"));
                row.put("ip","192.0.2.1"); row.put("torrentId",1L); row.put("seeding",true);
                mapper.swarm(1,List.of(row),120000,600000);
                row.put("upload",160L); row.put("download",60L);
                row.put("at",OffsetDateTime.parse("2026-01-02T00:00:30Z"));
                row.put("observationKey",UUID.randomUUID().toString());
                mapper.swarm(1,List.of(row),120000,600000);
                mapper.swarm(1,List.of(row),120000,600000);
                var ban=new HashMap<String,Object>();
                ban.put("key",UUID.randomUUID().toString()); ban.put("moment",row.get("at"));
                ban.put("ip","192.0.2.1"); ban.put("upload",20L); ban.put("download",10L);
                mapper.bans(1,List.of(ban),600000);
                mapper.bans(1,List.of(ban),600000);
                try (var statement=connection.createStatement();var result=statement.executeQuery("SELECT * FROM user_traffic_daily WHERE user_id=1 ORDER BY day")) {
                    assertTrue(result.next());
                    assertEquals("100",result.getString("upload"));
                    assertEquals(30,result.getBigDecimal("seeding").intValueExact());
                    assertTrue(result.next());
                    assertEquals("80",result.getString("upload"));
                    assertEquals("30",result.getString("download"));
                    assertEquals("20",result.getString("loss"));
                    assertEquals("25.00",result.getString("loss_ratio"));
                    assertEquals(30,result.getBigDecimal("seeding").intValueExact());
                    assertFalse(result.next());
                }
                try (var statement=connection.createStatement();var result=statement.executeQuery("SELECT * FROM user_traffic_daily WHERE user_id=2 AND day='2026-01-02'")) {
                    assertTrue(result.next());
                    assertEquals("30",result.getString("upload_ack"));
                    assertEquals("80",result.getString("download_ack"));
                }
                row.put("key",UUID.randomUUID().toString()); row.put("torrentId",2L); row.put("seeding",false);
                row.put("upload",0L); row.put("download",0L); row.put("initialUpload",0L); row.put("initialDownload",0L);
                row.put("at",OffsetDateTime.parse("2026-01-02T12:00:00Z"));
                mapper.swarm(1,List.of(row),120000,600000);
                row.put("at",OffsetDateTime.parse("2026-01-02T12:10:00Z"));
                mapper.swarm(1,List.of(row),120000,600000);
                // Out-of-order reports cannot extend or repeat activity time.
                row.put("at",OffsetDateTime.parse("2026-01-02T12:05:00Z"));
                mapper.swarm(1,List.of(row),120000,600000);
                try (var statement=connection.createStatement();var result=statement.executeQuery("SELECT downloading,seeding FROM user_traffic_daily WHERE user_id=1 AND day='2026-01-02'")) {
                    assertTrue(result.next());
                    assertEquals(120,result.getBigDecimal(1).intValueExact());
                    assertEquals(30,result.getBigDecimal(2).intValueExact());
                }
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO user_traffic_daily (user_id, day, upload, downloading)
                            VALUES (3, '2026-01-02', 9007199254740993, 0.25), (3, '2026-01-04', 5, 0)
                            """);
                }
                var traffic = session.getMapper(UserTrafficStatisticsMapper.class);
                var network = traffic.network(LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-04"),
                        new NetworkTrafficAccess(true, true, true, true, true));
                assertEquals(List.of("2026-01-02", "2026-01-04"),
                        network.stream().map(entry -> entry.get("day")).toList());
                assertEquals("9007199254741073", network.getFirst().get("upload"));
                assertEquals("30", network.getFirst().get("uploadAck"));
                assertEquals("80", network.getFirst().get("downloadAck"));
                assertEquals("120.250000", network.getFirst().get("downloading"));
                assertEquals("20", network.getFirst().get("totalLoss"));
                assertEquals("6.66666667", new java.math.BigDecimal(network.getFirst().get("averageLoss"))
                        .setScale(8, java.math.RoundingMode.HALF_UP).toPlainString());
                assertEquals("5", network.getLast().get("upload"));
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                            INSERT INTO user_traffic_daily (user_id, day, upload, loss)
                            SELECT id, '2026-01-05',
                                   CASE id WHEN 99 THEN 10 WHEN 100 THEN 9010000 ELSE 10000 END,
                                   CASE id WHEN 1 THEN 0 WHEN 100 THEN 9010 ELSE 10 END
                            FROM generate_series(1, 100) AS users(id)
                            """);
                }
                var day = LocalDate.parse("2026-01-05");
                var averages = traffic.network(day, day,
                        new NetworkTrafficAccess(false, false, false, true, false)).getFirst();
                assertEquals("10", new java.math.BigDecimal(averages.get("averageLoss")).stripTrailingZeros().toPlainString());
                assertEquals("0.1", new java.math.BigDecimal(averages.get("lossRatio")).stripTrailingZeros().toPlainString());
                assertEquals(Set.of("day", "averageLoss", "lossRatio"), averages.keySet());
                var combined = traffic.network(day.minusDays(1), day,
                        new NetworkTrafficAccess(true, true, true, true, true));
                assertEquals("5", combined.getFirst().get("upload"));
                assertEquals(averages.get("averageLoss"), combined.getLast().get("averageLoss"));
                assertEquals(averages.get("lossRatio"), combined.getLast().get("lossRatio"));
                assertEquals("9990", combined.getLast().get("totalLoss"));
                assertEquals(Map.of("day", "2026-01-05", "totalLoss", "9990"),
                        traffic.network(day, day, new NetworkTrafficAccess(false, false, false, false, true)).getFirst());
                session.rollback();
            }
        }
    }
}
