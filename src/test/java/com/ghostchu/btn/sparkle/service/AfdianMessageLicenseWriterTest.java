package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.mapper.AfdianMessageLicenseMapper;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfdianMessageLicenseWriterTest {
    private final AfdianMessageLicenseMapper mapper = mock(AfdianMessageLicenseMapper.class);
    private final AfdianMessageLicenseWriter writer = new AfdianMessageLicenseWriter(mapper);

    @Test
    void repeatedScansSkipExistingLicenseWithoutConsumingAgain() throws Exception {
        when(mapper.existing("key")).thenReturn(existing());
        when(mapper.matchStatus(1L)).thenReturn("EXACT");
        assertEquals("DUPLICATE", writer.register("user", "2", decoded(""), 42L));
        assertEquals("DUPLICATE", writer.register("user", "3", decoded(""), 42L));
        verify(mapper, never()).insert(any(), anyString(), anyString(), anyString(), any());
        verify(mapper, never()).match(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void retriesUnmatchedLicenseWhenOrderLaterAppears() throws Exception {
        when(mapper.existing("key")).thenReturn(existing());
        when(mapper.matchStatus(1L)).thenReturn("UNMATCHED");
        when(mapper.exact("user", "order-1")).thenReturn(order());
        assertEquals("RECONCILED", writer.register("user", "2", decoded(",\"paymentOrderId\":\"order-1\""), 42L));
        verify(mapper).match(1L, "PRODUCT", "order-1", "sku", "EXACT");
        verify(mapper, never()).insert(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void refusesToMoveLicenseBetweenAccounts() throws Exception {
        when(mapper.existing("key")).thenReturn(existing());
        assertEquals("CONFLICT", writer.register("different-user", "2", decoded(""), 42L));
        verify(mapper, never()).insert(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void registersUnboundHistoricalLicenseAndLeavesAmbiguousMatchesPending() throws Exception {
        when(mapper.candidates(eq("user"), any())).thenReturn(List.of(order(), order()));
        when(mapper.insert(any(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        assertEquals("UNMATCHED", writer.register("user", "2", decoded(""), 42L));
        verify(mapper).insert(argThat(l -> l.getBtnUserId() == null && l.getLicenseKey().equals("key")),
                matches("[0-9a-f]{64}"), eq("2"), eq("UNMATCHED"), eq(42L));
    }

    @Test
    void matchesV1OnlyWhenOneNearbyTransactionExists() throws Exception {
        when(mapper.candidates(eq("user"), any())).thenReturn(List.of(order()));
        when(mapper.insert(any(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        assertEquals("IMPORTED", writer.register("user", "2", decoded(""), 42L));
        verify(mapper).insert(argThat(l -> l.getSourceOrderNo().equals("order-1") && l.getSourceType().equals("PRODUCT")),
                anyString(), eq("2"), eq("HEURISTIC"), eq(42L));
    }

    private PbhLicense existing() {
        return new PbhLicense().setId(1L).setProvider("AFDIAN").setExternalUserId("user");
    }

    @Test
    void manualMatchingRejectsOrderFromAnotherAccount() {
        var licenseId = java.util.UUID.randomUUID();
        when(mapper.pendingById(licenseId)).thenReturn(existing());
        assertThrows(com.ghostchu.btn.sparkle.exception.BusinessException.class,
                () -> writer.resolve(new com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.MatchRequest(licenseId, "other-order"), 42L));
        verify(mapper).exact("user", "other-order");
        verify(mapper, never()).match(anyLong(), anyString(), anyString(), any(), anyString());
    }

    private MemberTransaction order() {
        return new MemberTransaction().setExternalTransactionId("order-1").setTransactionType("PRODUCT").setSku("sku");
    }

    private PbhLicenseDecoder.Decoded decoded(String extra) throws Exception {
        var now = OffsetDateTime.now();
        return new PbhLicenseDecoder.Decoded("key", new ObjectMapper().readTree(PbhLicenseDecoderTest.payload(extra)), now, now, now.plusYears(100));
    }
}
