package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.service.dto.PeerConcurrency;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
class PeerConcurrencyPostgresIntegrationTest {
    @Test
    void countsRowsInScopeAndIncludesCompletedPeersInBothCounts() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE swarm_tracker (peer_ip inet, last_time_seen timestamptz, peer_progress float8);
                        INSERT INTO swarm_tracker VALUES
                            ('192.0.2.1', '2026-02-01 00:00Z', 0.5),
                            ('192.0.2.1', '2026-02-01 12:00Z', 1.0),
                            ('192.0.2.2', '2026-02-01 12:00Z', 1.0),
                            ('192.0.2.3', '2026-02-01 12:00Z', 1.1),
                            ('192.0.2.3', '2026-02-01 12:00Z', NULL),
                            ('192.0.2.3', '2026-01-31 23:59Z', 1.0),
                            ('192.0.3.1', '2026-02-01 12:00Z', 1.0);
                        """);
            }
            var configuration = new Configuration();
            configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);
            String resource = "mapper/SwarmTrackerMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                var mapper = session.getMapper(SwarmTrackerMapper.class);
                var cutoff = OffsetDateTime.parse("2026-02-01T00:00:00Z");
                assertEquals(new PeerConcurrency(3, 3), mapper.countPeerConcurrency(cutoff, "192.0.2.0/24"));
                assertEquals(new PeerConcurrency(2, 1), mapper.countPeerConcurrency(cutoff, "192.0.2.1/32"));
                assertEquals(new PeerConcurrency(0, 0), mapper.countPeerConcurrency(cutoff, "2001:db8::/32"));
            }
        }
    }
}
