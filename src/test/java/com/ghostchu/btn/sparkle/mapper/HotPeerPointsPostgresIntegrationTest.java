package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
class HotPeerPointsPostgresIntegrationTest {
    @Test
    void scoresTopTorrentsPerHourAndDeduplicatesEachUsersPeersPerTorrent() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE public.userapp (id bigint, owner bigint);
                        CREATE TABLE public.swarm_tracker (
                            last_time_seen timestamptz, torrent_id bigint, userapps_id bigint,
                            peer_ip inet, peer_port integer, peer_id text
                        );
                        INSERT INTO public.userapp VALUES (1, 101), (2, 101), (3, 102);
                        INSERT INTO public.swarm_tracker VALUES
                            ('2026-02-01 00:00Z', 10, 1, '192.0.2.1', 6881, 'same-peer'),
                            ('2026-02-01 00:00Z', 10, 2, '192.0.2.2', 6881, 'same-peer'),
                            ('2026-02-01 00:00Z', 10, 3, '192.0.2.3', 6881, ''),
                            ('2026-02-01 00:00Z', 20, 1, '192.0.2.4', 6881, 'same-peer'),
                            ('2026-02-01 00:00Z', 20, 2, '192.0.2.5', 6881, 'other-peer'),
                            ('2026-02-01 00:00Z', 20, 3, '192.0.2.6', 6881, NULL),
                            ('2026-02-01 01:00Z', 10, 3, '192.0.2.3', 6881, 'one-peer'),
                            ('2026-02-01 01:00Z', 20, 1, '192.0.2.4', 6881, NULL),
                            ('2026-02-01 01:00Z', 20, 2, '192.0.2.5', 6881, NULL),
                            ('2026-02-01 02:00Z', 20, 3, '192.0.2.6', 6881, NULL);
                        """);
            }
            var configuration = new Configuration();
            configuration.addMapper(PointsDynamicMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                try {
                    var mapper = session.getMapper(PointsDynamicMapper.class);
                    var start = OffsetDateTime.parse("2026-02-01T00:00Z");
                    var rows = mapper.selectHotPeerEvents(start, start.plusHours(2), 1, 5).stream()
                            .sorted(Comparator.comparing((Map<String, Object> row) -> row.get("bucket").toString())
                                    .thenComparingLong(row -> ((Number) row.get("user_id")).longValue()))
                            .map(row -> List.of(row.get("user_id"), row.get("points")))
                            .toList();
                    assertEquals(List.of(List.of(101L, 5L), List.of(102L, 5L), List.of(101L, 10L)), rows);
                } finally {
                    session.rollback(true);
                }
            }
        }
    }
}
