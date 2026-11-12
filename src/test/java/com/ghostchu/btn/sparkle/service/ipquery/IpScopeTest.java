package com.ghostchu.btn.sparkle.service.ipquery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpScopeTest {
    @Test
    void ipv4UsesHost32ForLookupAnd24ForCommentsByDefault()
            throws Exception {
        IpScope scope = IpScope.parse("192.0.2.42");

        assertEquals(4, scope.version());
        assertEquals(32, scope.prefixLength());
        assertEquals("192.0.2.42/32", scope.canonicalCidr());
        assertEquals(24, scope.defaultCommentPrefix());
        assertEquals(
                "192.0.2.0/24",
                scope.withCommentPrefix(scope.defaultCommentPrefix()).canonicalCidr()
        );
        assertEquals("256", scope.withCommentPrefix(24).totalAddresses());
    }

    @Test
    void ipv6UsesHost128ForLookupAnd56ForCommentsByDefault()
            throws Exception {
        IpScope scope = IpScope.parse("2001:db8:1234:5678::1");

        assertEquals(6, scope.version());
        assertEquals(128, scope.prefixLength());
        assertEquals(56, scope.defaultCommentPrefix());
        assertEquals(
                "2001:db8:1234:5600::/56",
                scope.withCommentPrefix(56).canonicalCidr()
        );
        assertTrue(scope.totalAddresses().equals("1"));
    }

    @Test
    void explicitSupportedPrefixBecomesDefaultCommentScope()
            throws Exception {
        IpScope scope = IpScope.parse("2001:db8:abcd::/48");

        assertEquals(48, scope.prefixLength());
        assertEquals(48, scope.defaultCommentPrefix());
        assertEquals("2001:db8:abcd::/48", scope.canonicalCidr());
    }

    @Test
    void rejectsUnboundedCidrQueries() {
        assertThrows(
                Exception.class,
                () -> IpScope.parse("0.0.0.0/0")
        );
        assertThrows(
                Exception.class,
                () -> IpScope.parse("2001:db8::/16")
        );
    }
}
