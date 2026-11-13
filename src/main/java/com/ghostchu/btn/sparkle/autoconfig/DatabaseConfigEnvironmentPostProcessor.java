package com.ghostchu.btn.sparkle.autoconfig;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class DatabaseConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final String PROPERTY_SOURCE_NAME = "databaseConfigOverrides";

    @Override
    public void postProcessEnvironment(@NotNull ConfigurableEnvironment environment,
                                       @NotNull SpringApplication application) {
        String url = environment.getProperty(
                "spring.datasource.dynamic.datasource.master.url",
                environment.getProperty("spring.datasource.url")
        );
        if (url == null || url.isBlank()) {
            return;
        }
        String username = environment.getProperty(
                "spring.datasource.dynamic.datasource.master.username",
                environment.getProperty("spring.datasource.username")
        );
        String password = environment.getProperty(
                "spring.datasource.dynamic.datasource.master.password",
                environment.getProperty("spring.datasource.password")
        );

        Map<String, Object> properties;
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            properties = loadProperties(connection);
        } catch (Exception exception) {
            // The first startup has no config table yet; migration creates it after datasource startup.
            return;
        }
        applyProperties(environment, properties);
    }

    public static void refresh(ConfigurableEnvironment environment, DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            applyProperties(environment, loadProperties(connection));
        } catch (Exception exception) {
            // The application still has its YAML defaults if this post-migration refresh fails.
            log.warn("Unable to refresh business configuration after Flyway migration", exception);
        }
    }

    private static Map<String, Object> loadProperties(Connection connection) throws java.sql.SQLException {
        Map<String, Object> properties = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT config_key, config_value FROM app_config_entries "
                             + "WHERE config_key LIKE 'sparkle.%'"
             )) {
            while (resultSet.next()) {
                properties.put(resultSet.getString("config_key"), resultSet.getString("config_value"));
            }
        }
        return properties;
    }

    private static void applyProperties(ConfigurableEnvironment environment,
                                        Map<String, Object> properties) {
        MutablePropertySources sources = environment.getPropertySources();
        sources.remove(PROPERTY_SOURCE_NAME);
        if (properties.isEmpty()) {
            return;
        }

        var propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
        if (sources.contains("systemEnvironment")) {
            sources.addAfter("systemEnvironment", propertySource);
        } else if (sources.contains("systemProperties")) {
            sources.addAfter("systemProperties", propertySource);
        } else {
            sources.addFirst(propertySource);
        }
    }
}
