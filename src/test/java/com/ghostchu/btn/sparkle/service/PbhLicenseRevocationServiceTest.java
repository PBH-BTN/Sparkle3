package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseMapper;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseRevocationMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PbhLicenseRevocationServiceTest {
    final PbhLicenseRevocationMapper mapper = mock(PbhLicenseRevocationMapper.class);
    final PbhLicenseMapper licenses = mock(PbhLicenseMapper.class);
    final AfdianSettingsService settings = mock(AfdianSettingsService.class);
    final PbhLicenseRevocationService service = new PbhLicenseRevocationService(mapper, licenses,
            new PbhLicenseDecoder(), settings);

    @Test
    void unknownIncludingLicensesOutsideInventoryIsNotRevoked() {
        assertFalse(service.check(Map.of("licenseTo", "a".repeat(64))));
        verifyNoInteractions(licenses);
    }

    @Test
    void missingOrMalformedHashesCannotMatch() {
        assertFalse(service.check(Map.of()));
        assertFalse(service.check(Map.of("licenseTo", "plaintext")));
        verifyNoInteractions(mapper);
    }

    @Test
    void v1UsesClientDefaultsAndIgnoresV2OnlyFields() throws Exception {
        var payload = new ObjectMapper().readTree(PbhLicenseDecoderTest.payload(",\"orderId\":\"ignored\""));
        var hashes = PbhLicenseRevocationService.hashes(payload);
        assertEquals(PbhLicenseRevocationService.sha256("afdian"), hashes.get("type"));
        assertFalse(hashes.containsKey("orderId"));
        assertFalse(hashes.containsKey("description"));
    }

    @Test
    void inventoryRevocationUpdatesStatusAndStoresOnlyHashes() throws Exception {
        UUID id = UUID.randomUUID();
        when(licenses.selectOne(any())).thenReturn(new PbhLicense().setLicenseKey("secret")
                .setLicensePayload(PbhLicenseDecoderTest.payload(",\"licenseVersion\":2,\"orderId\":\"order\"")));
        service.revoke(id, null, " reason ", 7);
        verify(mapper).revoke(anyString(), argThat(s -> !s.contains("Test") && !s.contains("secret")), eq("reason"), eq(7L));
        verify(mapper).markLicense("secret", "reason");
    }

    @Test
    void independentSignedLicenseMatchesClientQueryExactly() throws Exception {
        var pair = PbhLicenseDecoderTest.pair();
        when(settings.licensePrivateKey()).thenReturn(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        String payload = PbhLicenseDecoderTest.payload(",\"licenseVersion\":2,\"orderId\":\"order\",\"description\":\"\"");
        String key = PbhLicenseDecoderTest.encode(pair, payload);
        service.revoke(null, key, "reason", 7);
        var hashes = PbhLicenseRevocationService.hashes(new ObjectMapper().readTree(payload));
        String fingerprint = PbhLicenseRevocationService.fingerprint(hashes);
        verify(mapper).revoke(eq(fingerprint), anyString(), eq("reason"), eq(7L));
        when(mapper.isRevoked(fingerprint)).thenReturn(true);
        assertTrue(service.check(hashes));
        hashes.remove("description");
        assertFalse(service.check(hashes));
        verifyNoInteractions(licenses);
    }

    @Test
    void rejectsInvalidKeysAmbiguousTargetsAndMissingReasons() {
        assertThrows(BusinessException.class, () -> service.revoke(null, "bad", "reason", 7));
        assertThrows(BusinessException.class, () -> service.revoke(UUID.randomUUID(), "bad", "reason", 7));
        assertThrows(BusinessException.class, () -> service.revoke(null, "bad", " ", 7));
        verifyNoInteractions(mapper);
    }
}
