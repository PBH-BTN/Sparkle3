package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessConfigMigrationTest {
    private static final Pattern SQL_DEFAULT_KEY = Pattern.compile(
            "(?m)^\\s*\\(\\s*(?:\\R\\s*)?'(sparkle\\.[^']+)',"
    );

    @Test
    void seedsEveryBusinessSettingFromApplicationYaml() throws Exception {
        Set<String> yamlKeys = new HashSet<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(
                "application.yaml",
                new ClassPathResource("application.yaml")
        )) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                if (DatabaseConfigService.isManagedKey(key)) {
                    yamlKeys.add(key);
                }
            }
        }

        String sql = new ClassPathResource(
                "db/migration/V44__business_config_defaults.sql"
        ).getContentAsString(StandardCharsets.UTF_8)
                + new ClassPathResource(
                "db/migration/V47__community_groups.sql"
        ).getContentAsString(StandardCharsets.UTF_8)
                + new ClassPathResource(
                "db/migration/V48__afdian_integration.sql"
        ).getContentAsString(StandardCharsets.UTF_8)
                + new ClassPathResource(
                "db/migration/V49__pbh_license_v2.sql"
        ).getContentAsString(StandardCharsets.UTF_8)
                + new ClassPathResource(
                "db/migration/V51__membership_currency_rates.sql"
        ).getContentAsString(StandardCharsets.UTF_8)
                + new ClassPathResource(
                "db/migration/V52__flarum_integration_config.sql"
        ).getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = SQL_DEFAULT_KEY.matcher(sql);
        Set<String> sqlKeys = new HashSet<>();
        while (matcher.find()) {
            assertTrue(sqlKeys.add(matcher.group(1)), "Duplicate SQL default: " + matcher.group(1));
        }

        assertEquals(yamlKeys, sqlKeys);
    }

    @Test
    void usesPlaceholdersForCredentialsAndIdentity() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V44__business_config_defaults.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertTrue(sql.contains(
                "('sparkle.github-sync.personal-access-token', 'foobar'"
        ));
        assertTrue(sql.contains("('sparkle.measure.globalping-token', 'foobar'"));
        assertTrue(sql.contains(
                "('sparkle.github-sync.org-name', 'example'"
        ));
        assertTrue(sql.contains(
                "('sparkle.user-contact-email.from-address', 'example@example.com'"
        ));
        assertFalse(sql.contains("github_pat_"));
        assertFalse(sql.contains("glsa_"));
        assertFalse(sql.contains("${"), "Flyway SQL must not contain Spring-style placeholders");
    }

    @Test
    void onlyExplicitProductionYamlKeysReplaceExistingValues() throws Exception {
        String sql = new ClassPathResource(
                "db/migration/V44__business_config_defaults.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertTrue(sql.contains(
                "('sparkle.ping.sync-banhistory.interval', '900000', "
                        + "'number', 'sparkle.ping', false, true, true)"
        ));
        assertTrue(sql.contains(
                "('sparkle.analyse.concurrent-download-analyse.threshold-concurrent', "
                        + "'80', 'number', 'sparkle.analyse', false, true, true)"
        ));
        assertTrue(sql.contains(
                "('sparkle.analyse.mass-download-monitor.threshold-download', "
                        + "'10737418240', 'number', 'sparkle.analyse', false, true, false)"
        ));
        assertTrue(sql.contains("WHERE EXCLUDED.source = 'sql-default-overwrite'"));
        assertFalse(sql.contains("WHERE defaults.overwrite_existing"));
    }
}
