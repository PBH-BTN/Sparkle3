package com.ghostchu.btn.sparkle.service.btn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtnConfigCatalogTest {
    @Test
    void exposesEveryYamlPingSetting() {
        Set<String> keys = BtnConfigCatalog.keys().stream()
                .filter(key -> key.startsWith("sparkle.ping."))
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(40, keys.size());
        assertTrue(keys.contains("sparkle.ping.reconfigure.random-initial-delay"));
        assertTrue(keys.contains("sparkle.ping.heartbeat.queue.max-size"));
        assertTrue(keys.contains("sparkle.ping.rule-ip-allowlist.pow-captcha"));
        assertTrue(keys.contains("sparkle.ping.query-ip.iframe-endpoint"));
    }

    @Test
    void catalogMatchesApplicationYaml() throws Exception {
        Set<String> yamlKeys = new HashSet<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(
                "application.yaml",
                new ClassPathResource("application.yaml")
        )) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (name.startsWith("sparkle.ping.")) {
                    yamlKeys.add(name);
                }
            }
        }

        Set<String> catalogPingKeys = BtnConfigCatalog.keys().stream()
                .filter(key -> key.startsWith("sparkle.ping."))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(yamlKeys, catalogPingKeys);
    }

    @Test
    void includesOtherBusinessSettingsMigratedFromYaml() {
        Set<String> keys = new HashSet<>(BtnConfigCatalog.keys());

        assertTrue(keys.contains("sparkle.root-url"));
        assertTrue(keys.contains("sparkle.chn-root-url"));
        assertTrue(keys.contains("sparkle.captcha.pow.difficulty-bits"));
        assertTrue(keys.contains("sparkle.query.query-ip.bans-counting-duration"));
        assertTrue(keys.contains("sparkle.ranking.weight.user-swarm-statistics.sent-traffic-other-ack"));
        assertTrue(keys.contains("sparkle.admin.user-ip-warning.cron"));
        assertTrue(keys.contains("sparkle.measure.prefix-length.ipv4"));
        assertTrue(BtnConfigCatalog.definition("sparkle.measure.globalping-token").sensitive());
        assertTrue(keys.contains("sparkle.points.time-zone"));
        assertTrue(keys.contains("sparkle.version-alert.from-address"));
        assertTrue(keys.contains("sparkle.userapp.auto-account.holder-uid"));
    }

    @Test
    void everyBusinessSettingHasABtnFieldOrDedicatedUi() throws Exception {
        List<String> dedicatedPrefixes = List.of(
                "sparkle.analyse.",
                "sparkle.client-discovery.",
                "sparkle.homepage-statistics-refresh.",
                "sparkle.swarm-statistics-track.",
                "sparkle.footer.",
                "sparkle.user-contact-email.",
                "sparkle.github-sync."
        );
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(
                "application.yaml",
                new ClassPathResource("application.yaml")
        )) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                if (!key.startsWith("sparkle.")) {
                    continue;
                }
                assertTrue(
                        BtnConfigCatalog.definition(key) != null
                                || dedicatedPrefixes.stream().anyMatch(key::startsWith),
                        "Business config has no management UI: " + key
                );
            }
        }
    }
}
