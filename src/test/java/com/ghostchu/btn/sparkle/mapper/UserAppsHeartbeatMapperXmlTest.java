package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.converter.JsonbTypeHandler;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.sql.DriverManager;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserAppsHeartbeatMapperXmlTest {

    @Test
    void upsertResolvesGeoipTypeHandler() throws Exception {
        var statement = configuration().getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper.upsert"
        );
        var geoipMapping = statement.getBoundSql(new UserappsHeartbeat()).getParameterMappings().stream()
                .filter(mapping -> "geoip".equals(mapping.getProperty()))
                .findFirst()
                .orElseThrow();

        assertInstanceOf(JsonbTypeHandler.class, geoipMapping.getTypeHandler());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".*/[^/?]*test[^/?]*(\\?.*)?")
    void findsLatestHeartbeatWithAnExistingOwnerAndMapsModerationStatus() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE userapps_heartbeat (userapp_id bigint, ip inet, last_seen_at timestamptz);
                        CREATE TEMP TABLE userapp (id bigint, owner bigint);
                        CREATE TEMP TABLE "user" (id bigint, nickname text, nickname_moderation_status text);
                        INSERT INTO "user" VALUES (1, 'older', 'APPROVED'), (2, 'newer', 'PENDING');
                        INSERT INTO userapp VALUES (10, 1), (20, 2), (30, 3);
                        INSERT INTO userapps_heartbeat VALUES
                            (20, '192.0.3.1', '2026-02-01 13:00Z'),
                            (30, '192.0.2.1', '2026-02-01 12:00Z'),
                            (20, '192.0.2.1', '2026-02-01 11:00Z'),
                            (10, '192.0.2.1', '2026-02-01 10:00Z'),
                            (10, '2001:db8::1', '2026-02-01 00:00Z');
                        """);
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration()).openSession(connection)) {
                var mapper = session.getMapper(UserAppsHeartbeatMapper.class);
                var cutoff = OffsetDateTime.parse("2026-02-01T00:00Z");
                var user = mapper.selectLatestUserInScope("192.0.2.0/24", cutoff);
                assertEquals(2L, user.getId());
                assertEquals("newer", user.getNickname());
                assertFalse(user.isNicknamePubliclyVisible());
                assertNull(mapper.selectLatestUserInScope("2001:db8::/32", cutoff));
            }
        }
    }

    private Configuration configuration() throws Exception {
        String resource = "mapper/UserappsHeartbeatMapper.xml";
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        return configuration;
    }
}
