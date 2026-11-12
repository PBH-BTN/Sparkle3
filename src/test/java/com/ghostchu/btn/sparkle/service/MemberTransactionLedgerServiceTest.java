package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.entity.MemberPlatformSyncRun;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.entity.MemberTransactionAuditEvent;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformSyncRunMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionAuditEventMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberTransactionLedgerServiceTest {
    private final MemberTransactionMapper transactionMapper =
            mock(MemberTransactionMapper.class);
    private final MemberPlatformSyncRunMapper syncRunMapper =
            mock(MemberPlatformSyncRunMapper.class);
    private final MemberTransactionAuditEventMapper auditMapper =
            mock(MemberTransactionAuditEventMapper.class);
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
    private final MemberTransactionLedgerService service =
            new MemberTransactionLedgerService(
                    transactionMapper,
                    syncRunMapper,
                    auditMapper,
                    objectMapper
            );

    @Test
    void createsAndAuditsNewProviderTransaction() throws Exception {
        stubRunInsert();
        when(transactionMapper.selectOne(any())).thenReturn(null);

        MemberTransactionLedgerService.SyncSession session =
                service.begin(MemberPlatform.PATREON);
        boolean changed = service.apply(session, transaction("TX-1"));

        assertEquals(1, session.recordsSeen());
        assertEquals(1, session.created());
        verify(transactionMapper).upsert(any(MemberTransaction.class));
        ArgumentCaptor<MemberTransactionAuditEvent> event =
                ArgumentCaptor.forClass(MemberTransactionAuditEvent.class);
        verify(auditMapper).insertEvent(event.capture());
        assertEquals("CREATED", event.getValue().getEventType());
        assertEquals(11L, event.getValue().getSyncRunId());
    }

    @Test
    void completeRetainsRowsMissingFromRemoteSnapshot() throws Exception {
        stubRunInsert();
        MemberPlatformSyncRun run = new MemberPlatformSyncRun()
                .setId(11L)
                .setProvider(MemberPlatform.PATREON.name())
                .setStatus("RUNNING");
        when(syncRunMapper.selectById(11L)).thenReturn(run);
        MemberTransaction existing = transaction("TX-OLD")
                .setId(7L)
                .setFirstSeenAt(OffsetDateTime.now().minusDays(2))
                .setRemotePresent(true);
        when(transactionMapper.selectList(any())).thenReturn(List.of(existing));

        MemberTransactionLedgerService.SyncSession session =
                service.begin(MemberPlatform.PATREON);
        int missing = service.complete(session);

        assertEquals(1, missing);
        assertEquals(1, session.missing());
        assertFalse(existing.getRemotePresent());
        verify(transactionMapper).updateById(existing);
        verify(transactionMapper, never()).deleteById(any(Long.class));
        verify(transactionMapper, never()).delete(any());
        ArgumentCaptor<MemberTransactionAuditEvent> event =
                ArgumentCaptor.forClass(MemberTransactionAuditEvent.class);
        verify(auditMapper).insertEvent(event.capture());
        assertEquals("MISSING_REMOTE", event.getValue().getEventType());
        assertEquals("COMPLETED", run.getStatus());
    }

    @Test
    void restoresPreviouslyMissingRemoteTransaction() throws Exception {
        stubRunInsert();
        OffsetDateTime occurredAt = OffsetDateTime.now().minusHours(1);
        MemberTransaction existing = transaction("TX-1")
                .setId(8L)
                .setFirstSeenAt(OffsetDateTime.now().minusDays(1))
                .setRemotePresent(false)
                .setOccurredAt(occurredAt);
        when(transactionMapper.selectOne(any())).thenReturn(existing);

        MemberTransactionLedgerService.SyncSession session =
                service.begin(MemberPlatform.PATREON);
        boolean changed = service.apply(
                session,
                transaction("TX-1").setOccurredAt(occurredAt)
        );

        assertTrue(changed);
        assertEquals(1, session.updated());
        ArgumentCaptor<MemberTransactionAuditEvent> event =
                ArgumentCaptor.forClass(MemberTransactionAuditEvent.class);
        verify(auditMapper).insertEvent(event.capture());
        assertEquals("RESTORED_REMOTE", event.getValue().getEventType());
    }

    private void stubRunInsert() {
        when(syncRunMapper.insert(any(MemberPlatformSyncRun.class))).thenAnswer(invocation -> {
            MemberPlatformSyncRun run = invocation.getArgument(0);
            run.setId(11L);
            return 1;
        });
    }

    private MemberTransaction transaction(String id) {
        return new MemberTransaction()
                .setProvider(MemberPlatform.PATREON.name())
                .setExternalTransactionId(id)
                .setExternalUserId("patron-1")
                .setExternalUserName("Patron")
                .setTransactionType("DONATION")
                .setAmount(new BigDecimal("12.00"))
                .setCurrency("USD")
                .setAmountCny(new BigDecimal("86.40"))
                .setAmountBase(new BigDecimal("12.00"))
                .setBaseCurrency("USD")
                .setTransactionStatus("COMPLETED")
                .setOccurredAt(OffsetDateTime.now());
    }
}
