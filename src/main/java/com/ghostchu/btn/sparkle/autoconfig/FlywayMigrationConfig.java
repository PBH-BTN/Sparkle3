package com.ghostchu.btn.sparkle.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class FlywayMigrationConfig {
    private static final long MIGRATION_LOCK_ID = 6_138_768_133_516_543_091L;
    private static final Set<String> DASHBOARD_VIEWS = Set.of(
            "dashboard_ban_daily_peer",
            "dashboard_ban_daily_client",
            "dashboard_ban_daily_geo"
    );
    private final ConfigurableEnvironment environment;

    public FlywayMigrationConfig(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Bean
    public FlywayMigrationStrategy sparkleFlywayMigrationStrategy() {
        return flyway -> {
            DataSource dataSource = flyway.getConfiguration().getDataSource();
            try (Connection connection = dataSource.getConnection()) {
                String schema = resolveSchema(connection, flyway);
                acquireMigrationLock(connection);
                try {
                    adoptLegacySchemaIfNeeded(flyway, connection, schema);
                    flyway.migrate();
                    DatabaseConfigEnvironmentPostProcessor.refresh(environment, dataSource);
                } finally {
                    releaseMigrationLockQuietly(connection);
                }
            } catch (SQLException exception) {
                throw new FlywayException("Failed to inspect the Sparkle3 database schema", exception);
            }
        };
    }

    private void adoptLegacySchemaIfNeeded(Flyway flyway,
                                           Connection connection,
                                           String schema) throws SQLException {
        String historyTable = flyway.getConfiguration().getTable();
        Set<String> relations = loadRelations(connection, schema);
        boolean hasHistory = relations.remove(historyTable);
        Set<String> columns = loadColumns(connection, schema);
        Set<String> indexes = loadIndexes(connection);
        boolean hypertable = isBanHistoryHypertable(connection, schema);
        boolean retentionPolicies = hasDashboardRetentionPolicies(connection, schema);

        Optional<String> baseline = LegacySchemaBaselineResolver.resolve(
                hasHistory,
                relations,
                columns,
                indexes,
                hypertable,
                retentionPolicies
        );
        if (baseline.isEmpty()) {
            return;
        }

        String version = baseline.get();
        log.info("Adopting existing Sparkle3 schema into Flyway at legacy version {}", version);
        Flyway.configure()
                .configuration(flyway.getConfiguration())
                .baselineVersion(version)
                .baselineDescription("Sparkle3 legacy schema " + version)
                .load()
                .baseline();
    }

    private String resolveSchema(Connection connection, Flyway flyway) throws SQLException {
        String configured = flyway.getConfiguration().getDefaultSchema();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String current = connection.getSchema();
        return current == null || current.isBlank() ? "public" : current;
    }

    private void acquireMigrationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
            statement.setLong(1, MIGRATION_LOCK_ID);
            statement.execute();
        }
    }

    private void releaseMigrationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, MIGRATION_LOCK_ID);
            statement.execute();
        }
    }

    private void releaseMigrationLockQuietly(Connection connection) {
        try {
            releaseMigrationLock(connection);
        } catch (SQLException exception) {
            log.warn("Unable to explicitly release the Flyway startup lock; closing the connection will release it",
                    exception);
        }
    }

    private Set<String> loadRelations(Connection connection, String schema) throws SQLException {
        Set<String> relations = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.relname
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ?
                  AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    relations.add(resultSet.getString(1));
                }
            }
        }
        return relations;
    }

    private Set<String> loadColumns(Connection connection, String schema) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = 'userapps_heartbeat'
                  AND column_name = 'geoip'
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1) + "." + resultSet.getString(2));
                }
            }
        }
        return columns;
    }

    private Set<String> loadIndexes(Connection connection) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT c.relname
                FROM pg_catalog.pg_class c
                WHERE c.relkind = 'i'
                  AND c.relname IN (
                      'dashboard_ban_daily_peer_bucket_dimension_idx',
                      'dashboard_ban_daily_client_bucket_dimension_idx'
                  )
                """
        )) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    indexes.add(resultSet.getString(1));
                }
            }
        }
        return indexes;
    }

    private boolean isBanHistoryHypertable(Connection connection, String schema) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM timescaledb_information.hypertables
                    WHERE hypertable_schema = ?
                      AND hypertable_name = 'ban_history'
                )
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private boolean hasDashboardRetentionPolicies(Connection connection, String schema) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(DISTINCT hypertable_name)
                FROM timescaledb_information.jobs
                WHERE proc_name = 'policy_retention'
                  AND hypertable_schema = ?
                  AND hypertable_name = ANY (?)
                """)) {
            statement.setString(1, schema);
            statement.setArray(2, connection.createArrayOf("text", DASHBOARD_VIEWS.toArray(String[]::new)));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == DASHBOARD_VIEWS.size();
            }
        } catch (SQLException exception) {
            return false;
        }
    }
}
