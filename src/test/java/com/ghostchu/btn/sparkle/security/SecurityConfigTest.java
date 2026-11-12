package com.ghostchu.btn.sparkle.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityConfigTest {
    @Test
    void skipsDefaultNoStoreHeadersForHashedAssets() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/app-123.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.writeDefaultCacheHeaders(request, response);

        assertNull(response.getHeader("Cache-Control"));
    }

    @Test
    void keepsNoStoreForDynamicResponses() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ui/footer");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.writeDefaultCacheHeaders(request, response);

        assertEquals(
                "no-cache, no-store, max-age=0, must-revalidate",
                response.getHeader("Cache-Control")
        );
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals("0", response.getHeader("Expires"));
    }
}
