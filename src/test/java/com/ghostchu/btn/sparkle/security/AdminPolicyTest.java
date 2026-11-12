package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPolicyTest {
    @Test
    void administratorInheritsModeratorAccess() {
        User admin = new User().setId(1L).setRole("admin");

        assertTrue(AdminPolicy.isAdmin(admin));
        assertTrue(AdminPolicy.isModerator(admin) || AdminPolicy.hasModeratorAccess(admin));
        assertTrue(AdminPolicy.hasModeratorAccess(admin));
        assertTrue(AdminPolicy.authorities(admin).stream()
                .anyMatch(authority -> AdminPolicy.MODERATOR_AUTHORITY.equals(
                        authority.getAuthority()
                )));
    }

    @Test
    void moderatorIsRestrictedToWhitelistedBackendModules() {
        User moderator = new User().setId(2L).setRole("moderator");

        assertFalse(AdminPolicy.isAdmin(moderator));
        assertTrue(AdminPolicy.hasModeratorAccess(moderator));
        assertTrue(AdminPolicy.canAccessModeratorModule("/api/admin/tasks"));
        assertTrue(AdminPolicy.canAccessModeratorModule("/api/admin/notices/12"));
        assertTrue(AdminPolicy.canAccessModeratorModule("/api/admin/userapps/91/ban"));
        assertTrue(AdminPolicy.canAccessModeratorModule("/api/admin/messages"));
        assertFalse(AdminPolicy.canAccessModeratorModule("/api/admin/users"));
        assertFalse(AdminPolicy.canAccessModeratorModule("/api/admin/settings"));
    }

    @Test
    void suspendedModeratorLosesAccess() {
        User moderator = new User()
                .setId(3L)
                .setRole("moderator")
                .setBannedAt(java.time.OffsetDateTime.now());

        assertFalse(AdminPolicy.hasModeratorAccess(moderator));
    }
}
