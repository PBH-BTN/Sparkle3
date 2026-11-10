package com.ghostchu.btn.sparkle.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrivacyLevelTest {
    @Test
    void onlyOpenPrivacyLevelsAllowContactEmail() {
        assertTrue(UserPrivacyLevel.DISABLED.isAllowContactEmail());
        assertTrue(UserPrivacyLevel.LOW.isAllowContactEmail());
        assertFalse(UserPrivacyLevel.MEDIUM.isAllowContactEmail());
        assertFalse(UserPrivacyLevel.HIGH.isAllowContactEmail());
    }
}
