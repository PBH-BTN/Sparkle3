package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.service.dto.UniversalCountDto;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
class StatisticsCountPostgresIntegrationTest {
    @Test
    void preservesCutoffsAndCountsApplicationsWithNoLastSeenTime() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        SET LOCAL TIME ZONE 'America/New_York';
                        CREATE TEMP TABLE userapp (last_seen_at timestamptz);
                        CREATE TEMP TABLE ban_history (populate_time timestamptz);
                        CREATE TEMP TABLE swarm_tracker (last_time_seen timestamptz);
                        INSERT INTO userapp VALUES
                            (NULL), ('2026-03-08 13:00:00Z'), ('2026-03-09 00:00:00Z'),
                            ('2026-03-02 13:00:00Z'), ('2026-02-23 13:00:00Z'),
                            ('2026-02-07 13:00:00Z'), ('2026-02-07 12:59:59Z');
                        INSERT INTO ban_history VALUES
                            (NULL), (NOW()), (NOW() - INTERVAL '1 day'),
                            (NOW() - INTERVAL '7 days'), (NOW() - INTERVAL '14 days'),
                            (NOW() - INTERVAL '30 days');
                        INSERT INTO swarm_tracker SELECT populate_time FROM ban_history;
                        """);
            }
            var configuration = new Configuration();
            configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);
            for (String mapper : new String[]{"Userapp", "BanHistory", "SwarmTracker"}) {
                String resource = "mapper/" + mapper + "Mapper.xml";
                try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                    new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
                }
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                var now = OffsetDateTime.parse("2026-03-09T13:00:00Z");
                assertEquals(new UserappMapper.ApplicationCounts(7, 5, 4, 3, 2),
                        session.getMapper(UserappMapper.class).countApplications(now));
                var expected = new UniversalCountDto(4, 3, 2, 1);
                assertEquals(expected, session.getMapper(BanHistoryMapper.class).countTimeStatistics());
                assertEquals(expected, session.getMapper(SwarmTrackerMapper.class).countTimeStatistics());
            }
        }
    }
}
