package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BtnConfigValidatorTest {
    @Test
    void acceptsRootUrlEndpointsAndDisabledCron() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(
                BtnConfigCatalog.key("syncBanHistory", "endpoint"),
                "{rooturl}/ping/customBanHistory"
        );
        values.put(
                BtnConfigCatalog.key("syncSwarm", "dataRetentionCron"),
                "-"
        );

        Map<String, String> normalized = BtnConfigValidator.validate(values, defaults());

        assertEquals("{rooturl}/ping/customBanHistory", normalized.get(
                BtnConfigCatalog.key("syncBanHistory", "endpoint")
        ));
        assertEquals("-", normalized.get(
                BtnConfigCatalog.key("syncSwarm", "dataRetentionCron")
        ));
    }

    @Test
    void rejectsInvalidProtocolRange() {
        Map<String, String> values = Map.of(
                BtnConfigCatalog.key("protocol", "minProtocolVersion"),
                "30"
        );

        assertThrows(
                BusinessException.class,
                () -> BtnConfigValidator.validate(values, defaults())
        );
    }

    @Test
    void rejectsBatchLargerThanQueue() {
        Map<String, String> values = Map.of(
                BtnConfigCatalog.key("heartbeatQueue", "batchSize"),
                "200"
        );
        Map<String, String> current = new LinkedHashMap<>(defaults());
        current.put(BtnConfigCatalog.key("heartbeatQueue", "maxSize"), "100");

        assertThrows(
                BusinessException.class,
                () -> BtnConfigValidator.validate(values, current)
        );
    }

    @Test
    void rejectsMalformedEndpointAndCron() {
        assertThrows(
                BusinessException.class,
                () -> BtnConfigValidator.validate(
                        Map.of(
                                BtnConfigCatalog.key("queryIp", "endpoint"),
                                "not a valid endpoint"
                        ),
                        defaults()
                )
        );
        assertThrows(
                BusinessException.class,
                () -> BtnConfigValidator.validate(
                        Map.of(
                                BtnConfigCatalog.key("heartbeat", "cleanupCron"),
                                "* * *"
                        ),
                        defaults()
                )
        );
    }

    @Test
    void validatesAlgorithmsEmailsAndSecrets() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(BtnConfigCatalog.key("captchaPow", "algorithm"), "SHA-256");
        values.put(
                BtnConfigCatalog.key("versionAlert", "fromAddress"),
                "sparkle@example.com"
        );
        values.put(BtnConfigCatalog.key("measure", "globalpingToken"), "secret-token");

        Map<String, String> normalized = BtnConfigValidator.validate(values, defaults());

        assertEquals("SHA-256", normalized.get(
                BtnConfigCatalog.key("captchaPow", "algorithm")
        ));
        assertEquals("secret-token", normalized.get(
                BtnConfigCatalog.key("measure", "globalpingToken")
        ));
        assertThrows(
                BusinessException.class,
                () -> BtnConfigValidator.validate(
                        Map.of("sparkle.version-alert.from-address", "not-an-email"),
                        defaults()
                )
        );
        assertThrows(
                BusinessException.class,
                () -> BtnConfigValidator.validate(
                        Map.of("sparkle.captcha.pow.algorithm", "definitely-not-a-digest"),
                        defaults()
                )
        );
    }

    private Map<String, String> defaults() {
        Map<String, String> values = new LinkedHashMap<>();
        for (BtnConfigCatalog.Definition definition : BtnConfigCatalog.definitions()) {
            values.put(definition.key(), definition.defaultValue());
        }
        return values;
    }
}
