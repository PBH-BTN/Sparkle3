package com.ghostchu.btn.sparkle.autoconfig;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(Ordered.LOWEST_PRECEDENCE)
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

        Map<String, Object> properties = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT config_key, config_value FROM app_config_entries"
             )) {
            while (resultSet.next()) {
                String key = resultSet.getString("config_key");
                if (!key.startsWith("spring.datasource.")
                        && !key.startsWith("spring.flyway.")) {
                    properties.put(key, resultSet.getString("config_value"));
                }
            }
        } catch (Exception exception) {
            // The first startup has no config table yet; migration creates it after datasource startup.
            return;
        }
        if (properties.isEmpty()) {
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();
        sources.remove(PROPERTY_SOURCE_NAME);
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
