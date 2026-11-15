package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
class PeerInsightsMapperPostgresIntegrationTest {
    @Test
    void banDistributionUsesOccurrenceTimeAndPublishesEmptyWindows() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_USER", "postgres"),
                System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_PASSWORD", ""))) {
            connection.setAutoCommit(false);
            var configuration = new Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(PeerInsightsMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection);
                 var statement = connection.createStatement()) {
                try {
                    statement.execute("""
                            CREATE TABLE public.ban_history (peer_ip inet, populate_time timestamptz, insert_time timestamptz);
                            INSERT INTO public.ban_history VALUES
                                ('192.0.2.1', NOW() - INTERVAL '2 hours', NOW()),
                                ('192.0.2.1', NOW() - INTERVAL '2 days', NOW()),
                                ('2001:db8::1', NOW() - INTERVAL '2 days', NOW()),
                                ('192.0.2.2', NOW() - INTERVAL '5 days', NOW()),
                                ('192.0.2.3', NOW() - INTERVAL '8 days', NOW()),
                                ('2001:db8::2', NOW() + INTERVAL '30 minutes', NOW());
                            """);

                    var mapper = session.getMapper(PeerInsightsMapper.class);
                    var counts = mapper.selectBanVersionSnapshots().stream()
                            .map(row -> List.of(row.getRangeHours().longValue(), row.getIpv4Peers(), row.getIpv6Peers()))
                            .toList();
                    assertEquals(List.of(List.of(24L, 1L, 0L), List.of(72L, 1L, 1L), List.of(168L, 2L, 1L)), counts);

                    statement.execute("TRUNCATE public.ban_history");
                    session.clearCache();
                    var empty = mapper.selectBanVersionSnapshots().stream()
                            .map(row -> List.of(row.getRangeHours().longValue(), row.getIpv4Peers(), row.getIpv6Peers()))
                            .toList();
                    assertEquals(List.of(List.of(24L, 0L, 0L), List.of(72L, 0L, 0L), List.of(168L, 0L, 0L)), empty);
                } finally {
                    session.rollback(true);
                }
            }
        }
    }
}
