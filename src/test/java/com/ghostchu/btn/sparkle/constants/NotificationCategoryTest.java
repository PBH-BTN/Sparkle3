package com.ghostchu.btn.sparkle.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationCategoryTest {
    @Test
    void legalAndAccountSecurityCannotBeDisabled() {
        assertTrue(NotificationCategory.LEGAL_COMPLIANCE.isMandatory());
        assertTrue(NotificationCategory.ACCOUNT_SECURITY.isMandatory());
        assertFalse(NotificationCategory.CLIENT_SECURITY.isMandatory());
        assertFalse(NotificationCategory.DIRECT_MESSAGE.isMandatory());
    }

    @Test
    void parsesCategoryCaseInsensitively() {
        assertTrue(NotificationCategory.parse("direct_message").isPresent());
        assertFalse(NotificationCategory.parse("unknown").isPresent());
    }
}
