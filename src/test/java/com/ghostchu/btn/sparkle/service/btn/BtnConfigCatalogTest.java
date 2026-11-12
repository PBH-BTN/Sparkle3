package com.ghostchu.btn.sparkle.service.btn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtnConfigCatalogTest {
    @Test
    void exposesEveryYamlPingSetting() {
        Set<String> keys = new HashSet<>(BtnConfigCatalog.keys());

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

        assertEquals(yamlKeys, new HashSet<>(BtnConfigCatalog.keys()));
    }
}
