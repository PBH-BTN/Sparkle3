package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_ALLOW_DESTRUCTIVE", matches = "true")
class AdminStoragePolicyIntegrationTest {
    @Test
    void executesVoidPolicyFunctionsWithoutBooleanConversion() {
        String url = System.getenv("SPARKLE_FLYWAY_TEST_URL");
        assertTrue(URI.create(url.substring("jdbc:".length())).getPath().contains("test"));
        var dataSource = new UnpooledDataSource("org.postgresql.Driver", url,
                System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_USER", "postgres"),
                System.getenv().getOrDefault("SPARKLE_FLYWAY_TEST_PASSWORD", ""));
        var jdbc = new JdbcTemplate(dataSource);
        String table = "admin_policy_test_" + UUID.randomUUID().toString().replace("-", "");
        String relation = "public." + table;
        jdbc.execute("CREATE TABLE " + relation + " (recorded_at timestamptz NOT NULL)");
        try {
            jdbc.queryForObject("SELECT create_hypertable(?::regclass, 'recorded_at')", Object.class, relation);
            var configuration = new Configuration(new Environment("admin-policy-test", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(AdminStorageMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
                var mapper = session.getMapper(AdminStorageMapper.class);
                mapper.addRetentionPolicy(relation, 7, 24);
                mapper.removeRetentionPolicy(relation);
                // Repeated saves of a disabled policy must also succeed.
                mapper.removeRetentionPolicy(relation);
                mapper.removeCompressionPolicy(relation);
                assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM timescaledb_information.jobs "
                        + "WHERE hypertable_schema = 'public' AND hypertable_name = ? AND proc_name = 'policy_retention'", Long.class, table));
            }
        } finally {
            jdbc.execute("DROP TABLE " + relation + " CASCADE");
        }
    }
}
