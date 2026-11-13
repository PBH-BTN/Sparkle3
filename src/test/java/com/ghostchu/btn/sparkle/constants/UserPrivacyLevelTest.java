package com.ghostchu.btn.sparkle.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrivacyLevelTest {
    @Test
    void mediumAndLowerPrivacyLevelsAllowContactEmail() {
        assertTrue(UserPrivacyLevel.DISABLED.isAllowContactEmail());
        assertTrue(UserPrivacyLevel.LOW.isAllowContactEmail());
        assertTrue(UserPrivacyLevel.MEDIUM.isAllowContactEmail());
        assertFalse(UserPrivacyLevel.HIGH.isAllowContactEmail());
    }

    @Test
    void lowAndPublicPrivacyLevelsExposePoints() {
        assertTrue(UserPrivacyLevel.DISABLED.isAllowPublicPoints());
        assertTrue(UserPrivacyLevel.LOW.isAllowPublicPoints());
        assertFalse(UserPrivacyLevel.MEDIUM.isAllowPublicPoints());
        assertFalse(UserPrivacyLevel.HIGH.isAllowPublicPoints());
    }

    @Test
    void usernameAndAvatarVisibilityMatchesPrivacyMatrix() {
        assertTrue(UserPrivacyLevel.DISABLED.isAllowFullUserName());
        assertFalse(UserPrivacyLevel.DISABLED.isAllowBlurredUsername());
        assertTrue(UserPrivacyLevel.DISABLED.isAllowOriginalAvatar());
        assertFalse(UserPrivacyLevel.DISABLED.isAllowBlurredAvatar());

        assertFalse(UserPrivacyLevel.LOW.isAllowFullUserName());
        assertTrue(UserPrivacyLevel.LOW.isAllowBlurredUsername());
        assertFalse(UserPrivacyLevel.LOW.isAllowOriginalAvatar());
        assertTrue(UserPrivacyLevel.LOW.isAllowBlurredAvatar());

        assertFalse(UserPrivacyLevel.MEDIUM.isAllowFullUserName());
        assertTrue(UserPrivacyLevel.MEDIUM.isAllowBlurredUsername());
        assertFalse(UserPrivacyLevel.MEDIUM.isAllowOriginalAvatar());
        assertTrue(UserPrivacyLevel.MEDIUM.isAllowBlurredAvatar());

        assertFalse(UserPrivacyLevel.HIGH.isAllowFullUserName());
        assertTrue(UserPrivacyLevel.HIGH.isAllowBlurredUsername());
        assertFalse(UserPrivacyLevel.HIGH.isAllowOriginalAvatar());
        assertFalse(UserPrivacyLevel.HIGH.isAllowBlurredAvatar());
    }
}
