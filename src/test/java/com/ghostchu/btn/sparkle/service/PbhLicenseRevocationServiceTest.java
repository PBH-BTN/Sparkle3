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
        service.revoke(id, null, " reason ", 7, false, false);
        verify(mapper).revoke(anyString(), argThat(s -> !s.contains("Test") && !s.contains("secret")), eq("reason"), eq(7L));
        verify(mapper).markLicense("secret", "reason");
        verify(mapper, never()).returnSlot(any());
        verify(mapper, never()).deleteLicense(any());
    }

    @Test
    void deletionRevokesAndReturnsOnlyTheSelectedSlotOnce() throws Exception {
        UUID id = UUID.randomUUID();
        when(licenses.selectOne(any())).thenReturn(new PbhLicense().setLicenseKey("secret")
                .setProvider("PATREON").setExternalUserId("account").setSourceOrderNo("order")
                .setLicensePayload(PbhLicenseDecoderTest.payload(",\"licenseVersion\":2")));
        when(mapper.returnSlot(id)).thenReturn(1, 0);
        service.revoke(id, null, "reason", 7, true, true);
        service.revoke(id, null, "reason", 7, true, false);
        verify(mapper, times(2)).markLicense("secret", "reason");
        verify(mapper).releaseOrder("PATREON", "account", "order");
        verify(mapper).deleteLicense(id);
    }

    @Test
    void deletionCanKeepTheSlotConsumed() throws Exception {
        UUID id = UUID.randomUUID();
        when(licenses.selectOne(any())).thenReturn(new PbhLicense().setLicenseKey("secret")
                .setLicensePayload(PbhLicenseDecoderTest.payload("")));
        service.revoke(id, null, "reason", 7, false, true);
        verify(mapper).deleteLicense(id);
        verify(mapper, never()).returnSlot(any());
    }

    @Test
    void missingLicenseAndIndependentSlotReturnsAreRejected() {
        assertThrows(BusinessException.class, () -> service.revoke(UUID.randomUUID(), null, "reason", 7, true, true));
        assertThrows(BusinessException.class, () -> service.revoke(null, "key", "reason", 7, true, false));
        assertThrows(BusinessException.class, () -> service.revoke(null, "key", "reason", 7, false, true));
        verifyNoInteractions(mapper);
    }

    @Test
    void unmatchedHistoryCannotBeDeletedLeavingAnUnresolvableSlotBlock() {
        when(licenses.selectOne(any())).thenReturn(new PbhLicense().setImportMatch("UNMATCHED"));
        var error = assertThrows(BusinessException.class,
                () -> service.revoke(UUID.randomUUID(), null, "reason", 7, false, true));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void independentSignedLicenseMatchesClientQueryExactly() throws Exception {
        var pair = PbhLicenseDecoderTest.pair();
        when(settings.licensePrivateKey()).thenReturn(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        String payload = PbhLicenseDecoderTest.payload(",\"licenseVersion\":2,\"orderId\":\"order\",\"description\":\"\"");
        String key = PbhLicenseDecoderTest.encode(pair, payload);
        service.revoke(null, key, "reason", 7, false, false);
        var hashes = PbhLicenseRevocationService.hashes(new ObjectMapper().readTree(payload));
        String fingerprint = PbhLicenseRevocationService.fingerprint(hashes);
        verify(mapper).revoke(eq(fingerprint), anyString(), eq("reason"), eq(7L));
        when(mapper.isRevoked(fingerprint)).thenReturn(true);
        assertTrue(service.check(hashes));
        hashes.remove("description");
        assertFalse(service.check(hashes));
        verify(licenses).lockKey("sparkle:pbh-import");
    }

    @Test
    void rejectsInvalidKeysAmbiguousTargetsAndMissingReasons() {
        assertThrows(BusinessException.class, () -> service.revoke(null, "bad", "reason", 7, false, false));
        assertThrows(BusinessException.class, () -> service.revoke(UUID.randomUUID(), "bad", "reason", 7, false, false));
        assertThrows(BusinessException.class, () -> service.revoke(null, "bad", " ", 7, false, false));
        verifyNoInteractions(mapper);
    }
}
