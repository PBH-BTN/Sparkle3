package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.AuditEventView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.ReconciliationView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.SyncRunView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.TransactionPage;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.TransactionView;
import com.ghostchu.btn.sparkle.entity.MemberPlatformSyncRun;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.entity.MemberTransactionAuditEvent;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformSyncRunMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionAuditEventMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberPlatformAdminService {
    private final MemberTransactionMapper transactionMapper;
    private final MemberPlatformSyncRunMapper syncRunMapper;
    private final MemberTransactionAuditEventMapper auditEventMapper;
    private final ObjectMapper objectMapper;

    public TransactionPage transactions(
            String provider,
            String keyword,
            String type,
            String status,
            Boolean remotePresent,
            OffsetDateTime occurredFrom,
            OffsetDateTime occurredTo,
            int requestedPage,
            int requestedSize
    ) throws BusinessException {
        int safePage = Math.max(1, requestedPage);
        int safeSize = Math.min(100, Math.max(1, requestedSize));
        QueryWrapper<MemberTransaction> query = new QueryWrapper<>();
        if (provider != null && !provider.isBlank()) {
            query.eq("provider", normalizeProvider(provider));
        }
        String term = keyword == null ? "" : keyword.trim();
        if (!term.isBlank()) {
            query.and(nested -> nested
                    .like("external_transaction_id", term)
                    .or()
                    .like("external_user_id", term)
                    .or()
                    .like("external_user_name", term)
                    .or()
                    .like("sku", term)
                    .or()
                    .like("product_name", term));
        }
        if (type != null && !type.isBlank()) {
            String normalized = type.trim().toUpperCase(Locale.ROOT);
            if (!List.of("PRODUCT", "DONATION").contains(normalized)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unsupported transaction type");
            }
            query.eq("transaction_type", normalized);
        }
        if (status != null && !status.isBlank()) {
            query.eq("transaction_status", status.trim());
        }
        if (remotePresent != null) {
            query.eq("remote_present", remotePresent);
        }
        if (occurredFrom != null) {
            query.ge("occurred_at", occurredFrom);
        }
        if (occurredTo != null) {
            query.le("occurred_at", occurredTo);
        }
        query.orderByDesc("occurred_at")
                .orderByDesc("id");
        Page<MemberTransaction> page = transactionMapper.selectPage(
                new Page<>(safePage, safeSize),
                query
        );
        return new TransactionPage(
                page.getRecords().stream().map(this::transactionView).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    public ReconciliationView reconciliation(
            String provider,
            int requestedLimit
    ) throws BusinessException {
        int limit = Math.min(200, Math.max(1, requestedLimit));
        QueryWrapper<MemberPlatformSyncRun> runs = new QueryWrapper<>();
        QueryWrapper<MemberTransactionAuditEvent> events = new QueryWrapper<>();
        if (provider != null && !provider.isBlank()) {
            String normalized = normalizeProvider(provider);
            runs.eq("provider", normalized);
            events.eq("provider", normalized);
        }
        runs.orderByDesc("started_at").last("LIMIT " + limit);
        events.orderByDesc("created_at").orderByDesc("id")
                .last("LIMIT " + limit);
        return new ReconciliationView(
                syncRunMapper.selectList(runs).stream()
                        .map(this::syncRunView)
                        .toList(),
                auditEventMapper.selectList(events).stream()
                        .map(this::auditEventView)
                        .toList()
        );
    }

    private TransactionView transactionView(MemberTransaction transaction) {
        return new TransactionView(
                transaction.getId(),
                transaction.getProvider(),
                transaction.getExternalTransactionId(),
                transaction.getExternalUserId(),
                transaction.getExternalUserName(),
                transaction.getTransactionType(),
                transaction.getSku(),
                transaction.getProductName(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getAmountCny(),
                transaction.getAmountBase(),
                transaction.getBaseCurrency(),
                transaction.getTransactionStatus(),
                transaction.getOccurredAt(),
                !Boolean.FALSE.equals(transaction.getRemotePresent()),
                Boolean.TRUE.equals(transaction.getLicenseConsumed()),
                transaction.getFirstSeenAt(),
                transaction.getLastReconciledAt(),
                transaction.getLastChangedAt()
        );
    }

    private SyncRunView syncRunView(MemberPlatformSyncRun run) {
        return new SyncRunView(
                run.getId(),
                run.getProvider(),
                run.getStatus(),
                run.getStartedAt(),
                run.getCompletedAt(),
                value(run.getPagesFetched()),
                value(run.getRecordsSeen()),
                value(run.getInsertedCount()),
                value(run.getUpdatedCount()),
                value(run.getUnchangedCount()),
                value(run.getMissingCount()),
                run.getErrorMessage()
        );
    }

    private AuditEventView auditEventView(MemberTransactionAuditEvent event) {
        return new AuditEventView(
                event.getId(),
                event.getSyncRunId() == null ? 0 : event.getSyncRunId(),
                event.getProvider(),
                event.getExternalTransactionId(),
                event.getEventType(),
                changedFields(event.getChangedFields()),
                event.getCreatedAt()
        );
    }

    private List<String> changedFields(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeProvider(String provider) throws BusinessException {
        return MemberPlatform.parse(provider)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported member platform"
                ))
                .name();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
