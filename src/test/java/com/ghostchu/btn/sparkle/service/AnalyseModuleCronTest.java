package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyseModuleCronTest {
    @Test
    void treatsSpringsDisabledCronTokenAsDisabled() {
        assertTrue(AnalyseModuleCron.isDisabled("-"));
        assertTrue(AnalyseModuleCron.isDisabled("  -  "));
        assertTrue(AnalyseModuleCron.isDisabled(""));
        assertTrue(AnalyseModuleCron.isDisabled(null));
    }

    @Test
    void leavesExecutableCronExpressionsUntouched() {
        String expression = "0 0 * * * *";

        assertFalse(AnalyseModuleCron.isDisabled(expression));
        assertEquals(expression, AnalyseModuleCron.normalize(expression));
        assertEquals("-", AnalyseModuleCron.normalize("  -  "));
    }
}
