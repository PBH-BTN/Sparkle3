package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
class SwarmRankingPostgresIntegrationTest {
    @Test
    void individualRankingMatchesTheLeaderboardWithTiesAndExcludedUsers() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE user_swarm_statistic (
                            user_id bigint PRIMARY KEY, sent_traffic_self_report bigint NOT NULL,
                            sent_traffic_other_ack bigint NOT NULL, received_traffic_other_ack bigint NOT NULL,
                            received_traffic_self_report bigint NOT NULL
                        );
                        INSERT INTO user_swarm_statistic VALUES
                            (1, 4, 3, 2, 1), (2, 0, 8, 2, 0), (3, 9, 8, 7, 6), (4, 1, 2, 3, 4);
                        """);
            }
            var configuration = new Configuration();
            String resource = "mapper/SwarmStatisticTrack.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                var mapper = session.getMapper(UserSwarmStatisticMapper.class);
                List<Long> exclusions = List.of(3L);
                var leaderboard = mapper.calcUsersRanking(1, 1, 2, 1, exclusions);
                for (var user : leaderboard) {
                    assertEquals(user, mapper.calcUserRanking(user.getUserId(), 1, 1, 2, 1, exclusions));
                }
                assertEquals(2L, mapper.calcUserRanking(1L, 1, 1, 2, 1, exclusions).getRank());
                assertEquals(2L, mapper.calcUserRanking(2L, 1, 1, 2, 1, exclusions).getRank());
                assertEquals(3L, mapper.calcUserRanking(1L, 1, 1, 2, 1, List.of()).getRank());
                assertNull(mapper.calcUserRanking(3L, 1, 1, 2, 1, exclusions));
                assertNull(mapper.calcUserRanking(99L, 1, 1, 2, 1, exclusions));
            }
        }
    }
}
