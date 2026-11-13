package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.entity.MemberPlatformSyncRun;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.entity.MemberTransactionAuditEvent;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformSyncRunMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionAuditEventMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemberTransactionLedgerService {
    private final MemberTransactionMapper transactionMapper;
    private final MemberPlatformSyncRunMapper syncRunMapper;
    private final MemberTransactionAuditEventMapper auditEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public SyncSession begin(MemberPlatform provider) throws BusinessException {
        OffsetDateTime now = OffsetDateTime.now();
        syncRunMapper.markStaleRunning(provider.name());
        MemberPlatformSyncRun run = new MemberPlatformSyncRun()
                .setProvider(provider.name())
                .setStatus("RUNNING")
                .setStartedAt(now)
                .setPagesFetched(0)
                .setRecordsSeen(0)
                .setInsertedCount(0)
                .setUpdatedCount(0)
                .setUnchangedCount(0)
                .setMissingCount(0);
        try {
            syncRunMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    provider.name() + " synchronization is already running");
        }
        return new SyncSession(run.getId(), provider, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean applyWebhook(MemberTransaction candidate)
            throws BusinessException {
        if (candidate == null || candidate.getProvider() == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Webhook returned an invalid transaction");
        }
        MemberPlatform provider = MemberPlatform.parse(candidate.getProvider())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "Webhook returned an unsupported provider"
                ));
        return apply(
                new SyncSession(null, provider, OffsetDateTime.now()),
                candidate
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean apply(SyncSession session, MemberTransaction candidate)
            throws BusinessException {
        if (candidate == null
                || candidate.getProvider() == null
                || candidate.getExternalTransactionId() == null
                || candidate.getExternalTransactionId().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    session.provider().name() + " returned an invalid transaction");
        }
        OffsetDateTime now = OffsetDateTime.now();
        MemberTransaction existing = transactionMapper.selectOne(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", candidate.getProvider())
                        .eq("external_transaction_id",
                                candidate.getExternalTransactionId())
                        .last("LIMIT 1")
        );
        candidate.setRemotePresent(true)
                .setLastReconciledAt(now)
                .setLastSyncRunId(session.runId());
        if (existing == null) {
            candidate.setFirstSeenAt(now)
                    .setLastChangedAt(now)
                    .setLicenseConsumed(false);
            transactionMapper.upsert(candidate);
            session.created++;
            session.recordsSeen++;
            audit(session, candidate, "CREATED", List.of(), null, candidate);
            return true;
        }

        List<String> changedFields = changedFields(existing, candidate);
        candidate.setFirstSeenAt(existing.getFirstSeenAt());
        candidate.setLastChangedAt(changedFields.isEmpty()
                ? existing.getLastChangedAt()
                : now);
        transactionMapper.upsert(candidate);
        session.recordsSeen++;
        boolean restored = Boolean.FALSE.equals(existing.getRemotePresent());
        if (changedFields.isEmpty() && !restored) {
            session.unchanged++;
            return false;
        }
        session.updated++;
        if (!changedFields.isEmpty()) {
            audit(session, candidate, "UPDATED", changedFields, existing, candidate);
        }
        if (restored) {
            audit(session, candidate, "RESTORED_REMOTE", List.of(), existing, candidate);
        }
        return true;
    }

    public void recordPage(SyncSession session) {
        session.pages++;
    }

    /**
     * Rows the current run has not seen yet. Callers may verify a few of them
     * against the provider before {@link #complete(SyncSession, java.util.Set)}
     * decides whether they were really removed.
     */
    public List<MemberTransaction> missingCandidates(SyncSession session) {
        return transactionMapper.selectList(missingQuery(session));
    }

    @Transactional(rollbackFor = Exception.class)
    public int complete(SyncSession session) throws BusinessException {
        return complete(session, java.util.Set.of());
    }

    /**
     * Finish a run, marking rows that the provider no longer lists as missing.
     * Rows whose IDs are in {@code stillRemote} were confirmed by the provider
     * through a per-transaction lookup even though the history listing omitted
     * them, so they keep their presence instead of being flagged as deleted.
     */
    @Transactional(rollbackFor = Exception.class)
    public int complete(SyncSession session, java.util.Set<Long> stillRemote)
            throws BusinessException {
        List<MemberTransaction> missing = missingCandidates(session);
        java.util.Set<Long> verified = stillRemote == null
                ? java.util.Set.of()
                : stillRemote;
        OffsetDateTime now = OffsetDateTime.now();
        int markedMissing = 0;
        for (MemberTransaction transaction : missing) {
            if (transaction.getId() != null && verified.contains(transaction.getId())) {
                MemberTransaction previous = copyForAudit(transaction);
                boolean wasMissing = Boolean.FALSE.equals(transaction.getRemotePresent());
                transaction.setRemotePresent(true)
                        .setLastReconciledAt(now)
                        .setLastSyncRunId(session.runId());
                transactionMapper.updateById(transaction);
                if (wasMissing) {
                    audit(
                            session,
                            transaction,
                            "RESTORED_REMOTE",
                            List.of(),
                            previous,
                            transaction
                    );
                }
                continue;
            }
            MemberTransaction previous = copyForAudit(transaction);
            boolean previouslyPresent = !Boolean.FALSE.equals(
                    transaction.getRemotePresent()
            );
            transaction.setRemotePresent(false)
                    .setLastReconciledAt(now)
                    .setLastSyncRunId(session.runId());
            transactionMapper.updateById(transaction);
            markedMissing++;
            if (previouslyPresent) {
                session.missing++;
                audit(
                        session,
                        transaction,
                        "MISSING_REMOTE",
                        List.of("remotePresent"),
                        previous,
                        null
                );
            }
        }
        saveRun(session, "COMPLETED", null);
        return markedMissing;
    }

    private QueryWrapper<MemberTransaction> missingQuery(SyncSession session) {
        return new QueryWrapper<MemberTransaction>()
                .eq("provider", session.provider().name())
                .lt("first_seen_at", session.startedAt())
                .and(query -> query
                        .ne("last_sync_run_id", session.runId())
                        .or()
                        .isNull("last_sync_run_id"));
    }

    public void incomplete(SyncSession session, String reason) {
        saveRun(
                session,
                "INCOMPLETE",
                reason == null ? "Provider pagination did not complete" : reason
        );
    }

    public void fail(SyncSession session, Throwable error) {
        String message = error == null ? "Unknown synchronization failure"
                : error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        saveRun(session, "FAILED", message);
    }

    private void saveRun(SyncSession session, String status, String error) {
        MemberPlatformSyncRun run = syncRunMapper.selectById(session.runId());
        if (run == null) return;
        run.setStatus(status)
                .setCompletedAt(OffsetDateTime.now())
                .setPagesFetched(session.pages)
                .setRecordsSeen(session.recordsSeen)
                .setInsertedCount(session.created)
                .setUpdatedCount(session.updated)
                .setUnchangedCount(session.unchanged)
                .setMissingCount(session.missing)
                .setErrorMessage(error);
        syncRunMapper.updateById(run);
    }

    private void audit(
            SyncSession session,
            MemberTransaction transaction,
            String eventType,
            List<String> changedFields,
            MemberTransaction previous,
            MemberTransaction current
    ) throws BusinessException {
        try {
            auditEventMapper.insertEvent(new MemberTransactionAuditEvent()
                    .setSyncRunId(session.runId())
                    .setProvider(transaction.getProvider())
                    .setExternalTransactionId(transaction.getExternalTransactionId())
                    .setEventType(eventType)
                    .setChangedFields(objectMapper.writeValueAsString(changedFields))
                    .setPreviousData(previous == null
                            ? null
                            : objectMapper.writeValueAsString(snapshot(previous)))
                    .setCurrentData(current == null
                            ? null
                            : objectMapper.writeValueAsString(snapshot(current))));
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to write transaction audit event: "
                            + exception.getMessage());
        }
    }

    private List<String> changedFields(
            MemberTransaction previous,
            MemberTransaction current
    ) {
        Map<String, Boolean> changed = new LinkedHashMap<>();
        changed.put("externalUserId", !Objects.equals(
                previous.getExternalUserId(), current.getExternalUserId()));
        changed.put("externalUserName", !Objects.equals(
                previous.getExternalUserName(), current.getExternalUserName()));
        changed.put("transactionType", !Objects.equals(
                previous.getTransactionType(), current.getTransactionType()));
        changed.put("sku", !Objects.equals(previous.getSku(), current.getSku()));
        changed.put("productName", !Objects.equals(
                previous.getProductName(), current.getProductName()));
        changed.put("amount", !sameAmount(
                previous.getAmount(), current.getAmount()));
        changed.put("currency", !Objects.equals(
                previous.getCurrency(), current.getCurrency()));
        changed.put("amountCny", !sameAmount(
                previous.getAmountCny(), current.getAmountCny()));
        changed.put("amountBase", !sameAmount(
                previous.getAmountBase(), current.getAmountBase()));
        changed.put("baseCurrency", !Objects.equals(
                previous.getBaseCurrency(), current.getBaseCurrency()));
        changed.put("transactionStatus", !Objects.equals(
                previous.getTransactionStatus(), current.getTransactionStatus()));
        changed.put("occurredAt", !Objects.equals(
                previous.getOccurredAt(), current.getOccurredAt()));
        return changed.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private Map<String, Object> snapshot(MemberTransaction transaction) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("externalTransactionId", transaction.getExternalTransactionId());
        value.put("externalUserId", transaction.getExternalUserId());
        value.put("externalUserName", transaction.getExternalUserName());
        value.put("transactionType", transaction.getTransactionType());
        value.put("sku", transaction.getSku());
        value.put("productName", transaction.getProductName());
        value.put("amount", transaction.getAmount());
        value.put("currency", transaction.getCurrency());
        value.put("amountCny", transaction.getAmountCny());
        value.put("amountBase", transaction.getAmountBase());
        value.put("baseCurrency", transaction.getBaseCurrency());
        value.put("transactionStatus", transaction.getTransactionStatus());
        value.put("occurredAt", transaction.getOccurredAt());
        value.put("remotePresent", transaction.getRemotePresent());
        return value;
    }

    private MemberTransaction copyForAudit(MemberTransaction source) {
        return new MemberTransaction()
                .setProvider(source.getProvider())
                .setExternalTransactionId(source.getExternalTransactionId())
                .setExternalUserId(source.getExternalUserId())
                .setExternalUserName(source.getExternalUserName())
                .setTransactionType(source.getTransactionType())
                .setSku(source.getSku())
                .setProductName(source.getProductName())
                .setAmount(source.getAmount())
                .setCurrency(source.getCurrency())
                .setAmountCny(source.getAmountCny())
                .setAmountBase(source.getAmountBase())
                .setBaseCurrency(source.getBaseCurrency())
                .setTransactionStatus(source.getTransactionStatus())
                .setOccurredAt(source.getOccurredAt())
                .setRemotePresent(source.getRemotePresent());
    }

    public static final class SyncSession {
        private final Long runId;
        private final MemberPlatform provider;
        private final OffsetDateTime startedAt;
        private int pages;
        private int recordsSeen;
        private int created;
        private int updated;
        private int unchanged;
        private int missing;

        private SyncSession(
                Long runId,
                MemberPlatform provider,
                OffsetDateTime startedAt
        ) {
            this.runId = runId;
            this.provider = provider;
            this.startedAt = startedAt;
        }

        public int recordsSeen() {
            return recordsSeen;
        }

        public int created() {
            return created;
        }

        public int updated() {
            return updated;
        }

        public int unchanged() {
            return unchanged;
        }

        public int missing() {
            return missing;
        }

        public Long runId() {
            return runId;
        }

        public MemberPlatform provider() {
            return provider;
        }

        private OffsetDateTime startedAt() {
            return startedAt;
        }
    }
}
