package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BtnConfigValidatorTest {
    @Test
    void replaySuspensionAllowsIndependentLimitsAndValidatesEmailSubject() throws Exception {
        String section = "banReplaySuspension";
        String checkBatches = BtnConfigCatalog.key(section, "batchDetectionEnabled");
        String checkLogs = BtnConfigCatalog.key(section, "logDetectionEnabled");
        String batchLimit = BtnConfigCatalog.key(section, "batchLimit");
        String logLimit = BtnConfigCatalog.key(section, "logLimit");
        String subject = BtnConfigCatalog.key(section, "emailSubject");
        var values = Map.of(checkBatches, "true", checkLogs, "false", batchLimit, "175", logLimit, "12500",
                subject, "Application {{appId}} suspended");
        assertEquals(values, BtnConfigValidator.validate(values, defaults()));
        assertThrows(BusinessException.class, () -> BtnConfigValidator.validate(Map.of(subject, "line 1\nline 2"), defaults()));
        assertThrows(BusinessException.class, () -> BtnConfigValidator.validate(Map.of(logLimit, "0"), defaults()));
    }

    @Test
    void routeListsAllowClearingAndNormalizeKeywords() throws Exception {
        String isps = BtnConfigCatalog.key("rootUrls", "chnRouteIsps");
        String provinces = BtnConfigCatalog.key("rootUrls", "chnRouteProvinces");
        Map<String, String> result = BtnConfigValidator.validate(
                Map.of(isps, " 移动,移动\n电信 ", provinces, " , \n"), defaults());
        assertEquals("移动,电信", result.get(isps));
        assertEquals("", result.get(provinces));
    }

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
