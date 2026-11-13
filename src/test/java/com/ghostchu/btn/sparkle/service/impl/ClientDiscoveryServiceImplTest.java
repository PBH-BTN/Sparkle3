package com.ghostchu.btn.sparkle.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientDiscoveryServiceImplTest {
    @Test
    void acceptsMissingPeerClientName() {
        assertFalse(ClientDiscoveryServiceImpl.shouldSkipPeer("-qB4650-", null));
    }

    @Test
    void acceptsMissingPeerId() {
        assertFalse(ClientDiscoveryServiceImpl.shouldSkipPeer(null, "Gopeed dev"));
    }

    @Test
    void appliesPeerFilteringWhenBothValuesExist() {
        assertTrue(ClientDiscoveryServiceImpl.shouldSkipPeer(
                "-XL0012-abcdefghijkl",
                "-XL0012-client"
        ));
        assertTrue(ClientDiscoveryServiceImpl.shouldSkipPeer(
                "-qB4650-abcdefghijkl",
                "Gopeed dev"
        ));
        assertTrue(ClientDiscoveryServiceImpl.shouldSkipPeer(
                "FD6-abcdefghijklmnop",
                "Unknown"
        ));
        assertFalse(ClientDiscoveryServiceImpl.shouldSkipPeer(
                "-GP0001-abcdefghijkl",
                "Gopeed dev"
        ));
    }
}
