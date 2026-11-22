package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
class PeerTorrentCountPostgresIntegrationTest {
    @Test
    void countsEachTorrentOnceAcrossBothSourcesAndIncludesTheCutoff() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE ban_history (peer_ip inet, populate_time timestamptz, torrent_id bigint);
                        CREATE TEMP TABLE swarm_tracker (peer_ip inet, last_time_seen timestamptz, torrent_id bigint);
                        INSERT INTO ban_history VALUES
                            ('192.0.2.1', '2026-02-01 00:00Z', 1),
                            ('192.0.2.1', '2026-02-01 12:00Z', 1),
                            ('192.0.2.2', '2026-02-01 12:00Z', 2),
                            ('192.0.2.3', '2026-01-31 23:59Z', 4);
                        INSERT INTO swarm_tracker VALUES
                            ('192.0.2.2', '2026-02-01 12:00Z', 1),
                            ('192.0.2.2', '2026-02-01 12:00Z', 3),
                            ('192.0.3.1', '2026-02-01 12:00Z', 5),
                            ('2001:db8::1', '2026-02-01 12:00Z', 6);
                        """);
            }
            var configuration = new Configuration();
            String resource = "mapper/BanHistoryMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                var mapper = session.getMapper(BanHistoryMapper.class);
                var cutoff = OffsetDateTime.parse("2026-02-01T00:00Z");
                assertEquals(3, mapper.countPeerTorrents(cutoff, "192.0.2.0/24"));
                assertEquals(1, mapper.countPeerTorrents(cutoff, "192.0.2.1/32"));
                assertEquals(1, mapper.countPeerTorrents(cutoff, "2001:db8::/32"));
                assertEquals(0, mapper.countPeerTorrents(cutoff, "198.51.100.0/24"));
            }
        }
    }
}
