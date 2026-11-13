package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.ProviderUpdateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.ProviderView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.SyncOutcome;
import com.ghostchu.btn.sparkle.controller.ui.dto.MemberPlatformPublicModels.ProviderBindingView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MemberPlatformPublicModels.TransactionView;
import com.ghostchu.btn.sparkle.entity.MemberPlatformBinding;
import com.ghostchu.btn.sparkle.entity.MemberPlatformConfig;
import com.ghostchu.btn.sparkle.entity.MemberPlatformSyncRun;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformConfigMapper;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformSyncRunMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import com.ghostchu.btn.sparkle.service.membership.MemberPlatformAdapter;
import com.ghostchu.btn.sparkle.service.membership.MemberPlatformCredentials;
import com.ghostchu.btn.sparkle.service.membership.MemberPlatformSnapshot;
import com.ghostchu.btn.sparkle.service.membership.PlatformTransaction;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provider-neutral membership ledger.
 *
 * <p>Afdian keeps its dedicated integration because of its signed request
 * format. Patreon and Ko-fi are handled by first-class adapters that own their
 * official API or webhook. Providers without a public API stay visible to
 * administrators but are explicitly unsupported instead of asking them to type
 * an arbitrary API URL.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberPlatformService {
    private static final Set<String> COMPLETED_STATUSES = Set.of(
            "2",
            "PAID",
            "COMPLETED",
            "SUCCEEDED",
            "SUCCESS",
            "ACTIVE"
    );
    /**
     * Upper bound on per-transaction presence checks during one sync run. A
     * provider that suddenly drops thousands of rows is far more likely to have
     * returned a partial listing than to have deleted them all.
     */
    private static final int REMOTE_VERIFY_LIMIT = 100;

    private final MemberPlatformConfigMapper configMapper;
    private final MemberPlatformBindingMapper bindingMapper;
    private final MemberTransactionMapper transactionMapper;
    private final MemberPlatformSyncRunMapper syncRunMapper;
    private final ObjectMapper objectMapper;
    private final ExchangeRateService exchangeRateService;
    private final AfdianSettingsService afdianSettingsService;
    private final MemberTransactionLedgerService ledgerService;
    private final List<MemberPlatformAdapter> adapters;
    private final Map<MemberPlatform, ReentrantLock> syncLocks =
            new ConcurrentHashMap<>();

    public SettingsView settings() {
        return new SettingsView(
                Arrays.stream(MemberPlatform.values())
                        .filter(provider -> provider != MemberPlatform.AFDIAN)
                        .map(this::providerView)
                        .toList(),
                exchangeRateService.baseCurrency(),
                exchangeRateService.apiUrl()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ProviderView update(
            MemberPlatform provider,
            ProviderUpdateRequest request,
            @Nullable Long operatorId
    ) throws BusinessException {
        MemberPlatformAdapter adapter = requireAdapter(provider);
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Member platform configuration is required");
        }
        String currency = exchangeRateService.validateCurrency(
                request.currency() == null || request.currency().isBlank()
                        ? adapter.defaultCurrency()
                        : request.currency()
        );
        BigDecimal exchangeRate;
        try {
            exchangeRate = new BigDecimal(
                    request.exchangeRate() == null
                            || request.exchangeRate().isBlank()
                            ? "1"
                            : request.exchangeRate().trim()
            );
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Fallback exchange rate must be numeric");
        }
        if (exchangeRate.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Fallback exchange rate must be greater than zero");
        }

        MemberPlatformConfig config = configMapper.selectById(provider.name());
        boolean insert = config == null;
        if (insert) {
            config = new MemberPlatformConfig()
                    .setProvider(provider.name());
        }
        config.setEnabled(request.enabled())
                .setCurrency(currency)
                .setExchangeRate(exchangeRate)
                .setUpdatedAt(OffsetDateTime.now())
                .setUpdatedBy(operatorId);
        if (adapter.configFields().contains("accountId")) {
            config.setAccountId(validatedText(
                    request.accountId(),
                    255,
                    "Member account or campaign ID is too long"
            ));
        }
        if (adapter.configFields().contains("accessToken")) {
            if (Boolean.TRUE.equals(request.clearAccessToken())) {
                config.setAccessToken(null);
            } else if (request.accessToken() != null
                    && !request.accessToken().isBlank()) {
                config.setAccessToken(validatedText(
                        request.accessToken(),
                        4_096,
                        "Member platform access credential is too long"
                ));
            }
        }
        if (adapter.configFields().contains("webhookToken")) {
            if (Boolean.TRUE.equals(request.clearWebhookToken())) {
                config.setWebhookToken(null);
            } else if (request.webhookToken() != null
                    && !request.webhookToken().isBlank()) {
                config.setWebhookToken(validatedText(
                        request.webhookToken(),
                        4_096,
                        "Webhook verification token is too long"
                ));
            }
        }
        if (insert) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return providerView(provider);
    }

    public List<ProviderBindingView> userProviders(long userId) {
        List<ProviderBindingView> values = new ArrayList<>();
        values.add(bindingView(userId, MemberPlatform.AFDIAN, false));
        for (MemberPlatform provider : MemberPlatform.values()) {
            if (provider == MemberPlatform.AFDIAN) {
                continue;
            }
            values.add(bindingView(userId, provider, true));
        }
        return values.stream()
                .filter(ProviderBindingView::configured)
                .toList();
    }

    public boolean anyConfigured() {
        return configMapper.selectList(
                new QueryWrapper<MemberPlatformConfig>().eq("enabled", true)
        ).stream().anyMatch(entity -> {
            MemberPlatformAdapter adapter = adapter(
                    MemberPlatform.parse(entity.getProvider()).orElse(null)
            );
            return adapter != null && ready(adapter, mapConfig(entity));
        });
    }

    public ProviderBindingView bind(
            long userId,
            MemberPlatform provider,
            String proof
    ) throws BusinessException {
        MemberPlatformAdapter adapter = requireAdapter(provider);
        ConfigRow config = config(provider);
        if (config == null || !ready(adapter, config)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.name() + " integration is not configured");
        }
        String normalizedProof = proof == null ? "" : proof.trim();
        if (normalizedProof.length() < 3 || normalizedProof.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "A valid order, receipt, or account identifier is required");
        }
        TransactionRow transaction = transactionByProof(provider, normalizedProof);
        if (transaction == null) {
            PlatformTransaction resolved = adapter.resolveProof(
                    credentials(adapter, config),
                    normalizedProof
            );
            if (resolved != null) {
                ledgerService.applyWebhook(toEntity(provider, config, resolved));
                transaction = new TransactionRow(
                        resolved.externalUserId(),
                        resolved.externalUserName(),
                        resolved.status()
                );
            }
        }
        if (transaction == null && adapter.supportsHistorySync()) {
            syncProvider(provider);
            transaction = transactionByProof(provider, normalizedProof);
        }
        if (transaction == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "No matching " + provider.name()
                            + " transaction was found in the cached history");
        }
        if (!completed(transaction.status())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Only completed transactions can be used for account linking");
        }
        try {
            bindingMapper.insert(new MemberPlatformBinding()
                    .setProvider(provider.name())
                    .setBtnUserId(userId)
                    .setExternalUserId(transaction.externalUserId())
                    .setExternalUserName(blankToNull(transaction.externalUserName()))
                    .setProof(normalizedProof)
                    .setLastSyncedAt(OffsetDateTime.now()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This " + provider.name()
                            + " account or Sparkle user already has a different binding");
        }
        return bindingView(userId, provider, true);
    }

    public void unbind(long userId, MemberPlatform provider) throws BusinessException {
        requireAdapter(provider);
        if (bindingMapper.delete(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", provider.name())
                        .eq("btn_user_id", userId)
        ) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "No " + provider.name() + " account is linked");
        }
    }

    public int ingestWebhook(
            MemberPlatform provider,
            @Nullable String token,
            @Nullable String rawBody,
            JsonNode payload,
            Map<String, String> headers
    ) throws BusinessException {
        MemberPlatformAdapter adapter = requireAdapter(provider);
        if (!adapter.supportsWebhook()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    provider.name() + " does not send webhooks");
        }
        ConfigRow config = config(provider);
        if (config == null || !ready(adapter, config)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.name() + " integration is not configured");
        }
        Map<String, String> mergedHeaders = new java.util.HashMap<>(headers);
        if (token != null && !token.isBlank()) {
            mergedHeaders.putIfAbsent("x-sparkle-token", token);
        }
        List<PlatformTransaction> transactions = adapter.parseWebhook(
                credentials(adapter, config),
                rawBody,
                payload,
                mergedHeaders
        );
        int accepted = 0;
        for (PlatformTransaction transaction : transactions) {
            ledgerService.applyWebhook(toEntity(provider, config, transaction));
            accepted++;
        }
        return accepted;
    }

    public SyncOutcome syncProvider(MemberPlatform provider) throws BusinessException {
        MemberPlatformAdapter adapter = requireAdapter(provider);
        if (!adapter.supportsHistorySync()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    provider.name() + " only delivers real-time webhooks; "
                            + "there is no history API to synchronize");
        }
        ConfigRow config = config(provider);
        if (config == null || !ready(adapter, config)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.name() + " API is not configured");
        }
        ReentrantLock lock = syncLocks.computeIfAbsent(
                provider,
                ignored -> new ReentrantLock()
        );
        if (!lock.tryLock()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    provider.name() + " synchronization is already running");
        }
        try {
            return synchronizeProvider(provider, adapter, config);
        } finally {
            lock.unlock();
        }
    }

    private SyncOutcome synchronizeProvider(
            MemberPlatform provider,
            MemberPlatformAdapter adapter,
            ConfigRow config
    ) throws BusinessException {
        MemberTransactionLedgerService.SyncSession session =
                ledgerService.begin(provider);
        try {
            MemberPlatformCredentials credentials = credentials(adapter, config);
            MemberPlatformSnapshot snapshot = adapter.fetchHistory(credentials);
            for (PlatformTransaction transaction : snapshot.transactions()) {
                ledgerService.apply(session, toEntity(provider, config, transaction));
            }
            if ((config.accountId() == null || config.accountId().isBlank())
                    && snapshot.discoveredAccountId() != null
                    && !snapshot.discoveredAccountId().isBlank()) {
                configMapper.update(null,
                        new LambdaUpdateWrapper<MemberPlatformConfig>()
                                .set(MemberPlatformConfig::getAccountId,
                                        snapshot.discoveredAccountId())
                                .set(MemberPlatformConfig::getUpdatedAt,
                                        OffsetDateTime.now())
                                .eq(MemberPlatformConfig::getProvider, provider.name()));
            }
            if (snapshot.complete()) {
                ledgerService.complete(
                        session,
                        remotelyPresentIds(adapter, credentials, session)
                );
            } else {
                // A short listing must not mark the rows it omitted as deleted.
                ledgerService.incomplete(
                        session,
                        "Provider returned a shorter history than it reports; "
                                + "transaction presence was left unchanged"
                );
            }
            markBindingsSynced(provider);
            return new SyncOutcome(
                    provider.name(),
                    true,
                    session.recordsSeen(),
                    snapshot.remoteCount(),
                    null
            );
        } catch (BusinessException exception) {
            ledgerService.fail(session, exception);
            markBindingsFailed(provider, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            ledgerService.fail(session, exception);
            markBindingsFailed(provider, exception.getMessage());
            throw exception;
        }
    }

    /**
     * Ask the provider about the transactions its own history listing did not
     * include. Only transactions confirmed to still exist are returned, so the
     * ledger can keep their presence instead of flagging them as deleted.
     */
    private Set<Long> remotelyPresentIds(
            MemberPlatformAdapter adapter,
            MemberPlatformCredentials credentials,
            MemberTransactionLedgerService.SyncSession session
    ) {
        List<MemberTransaction> candidates = ledgerService.missingCandidates(session);
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<Long> present = new HashSet<>();
        int checked = 0;
        for (MemberTransaction candidate : candidates) {
            if (checked >= REMOTE_VERIFY_LIMIT) {
                log.warn("Stopped verifying provider presence after {} transactions; "
                                + "{} rows in run {} will be reconciled as missing",
                        checked, candidates.size() - checked, session.runId());
                break;
            }
            checked++;
            try {
                if (Boolean.TRUE.equals(adapter.transactionExists(
                        credentials,
                        candidate.getExternalTransactionId()
                ))) {
                    present.add(candidate.getId());
                }
            } catch (BusinessException exception) {
                log.warn("Unable to verify {} transaction {} remotely: {}",
                        candidate.getProvider(),
                        candidate.getExternalTransactionId(),
                        exception.getMessage());
            }
        }
        return present;
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 120_000)
    @MonitoredScheduledTask(
            id = "member-platform-sync",
            group = TaskGroup.SYNC,
            nameKey = "admin.tasks.items.member-platform-sync.name",
            descriptionKey = "admin.tasks.items.member-platform-sync.description",
            scheduleType = TaskScheduleType.FIXED_DELAY,
            intervalMillis = 21_600_000,
            initialDelayMillis = 120_000
    )
    public void scheduledSync() {
        syncAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public int recalculateTransactionBaseAmounts() throws BusinessException {
        List<MemberTransaction> transactions = transactionMapper.selectList(null);
        String baseCurrency = exchangeRateService.baseCurrency();
        for (MemberTransaction transaction : transactions) {
            String currency = transaction.getCurrency() == null
                    ? baseCurrency
                    : transaction.getCurrency();
            transaction.setAmountCny(exchangeRateService.convert(
                    transaction.getAmount(),
                    "CNY"
            ));
            transaction.setAmountBase(exchangeRateService.convert(
                    transaction.getAmount(),
                    currency
            ));
            transaction.setBaseCurrency(baseCurrency);
            transactionMapper.updateById(transaction);
        }
        return transactions.size();
    }

    public int syncAll() {
        int syncedCount = 0;
        for (MemberPlatform provider : MemberPlatform.values()) {
            MemberPlatformAdapter adapter = adapter(provider);
            if (adapter == null || !adapter.supportsHistorySync()) {
                continue;
            }
            ConfigRow config = config(provider);
            if (config == null || !ready(adapter, config)) {
                continue;
            }
            try {
                if (syncProvider(provider).success()) {
                    syncedCount++;
                }
            } catch (Exception exception) {
                log.warn("Scheduled {} synchronization failed: {}",
                        provider, exception.getMessage());
            }
        }
        return syncedCount;
    }

    public ProviderBindingView bindingView(
            long userId,
            MemberPlatform provider,
            boolean generic
    ) {
        MemberPlatformAdapter adapter = generic ? adapter(provider) : null;
        ConfigRow config = generic ? config(provider) : null;
        boolean configured = generic
                ? adapter != null && ready(adapter, config)
                : afdianConfigured();
        List<BindingRow> bindings = bindings(userId, provider);
        if (bindings.isEmpty()) {
            return new ProviderBindingView(
                    configured,
                    false,
                    provider.name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "0.00",
                    "0.00",
                    exchangeRateService.baseCurrency(),
                    0,
                    0,
                    0,
                    List.of()
            );
        }
        List<String> externalIds = bindings.stream()
                .map(BindingRow::externalUserId)
                .distinct()
                .toList();
        List<TransactionView> transactions = transactions(provider, externalIds);
        List<TransactionView> paid = transactions.stream()
                .filter(item -> item.remotePresent() && completed(item.status()))
                .toList();
        BigDecimal totalCny = paid.stream()
                .map(item -> new BigDecimal(item.amountCny()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBase = paid.stream()
                .map(item -> new BigDecimal(item.amountBase()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BindingRow single = bindings.size() == 1 ? bindings.getFirst() : null;
        return new ProviderBindingView(
                configured,
                true,
                provider.name(),
                single == null ? null : single.externalUserId(),
                single == null ? null : single.externalUserName(),
                single == null ? null : single.externalUserAvatar(),
                single == null ? null : single.proof(),
                bindings.stream()
                        .map(BindingRow::boundAt)
                        .filter(java.util.Objects::nonNull)
                        .min(OffsetDateTime::compareTo)
                        .orElse(null),
                bindings.stream()
                        .map(BindingRow::lastSyncedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(OffsetDateTime::compareTo)
                        .orElse(null),
                bindings.stream()
                        .map(BindingRow::lastError)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null),
                totalCny.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                totalBase.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                exchangeRateService.baseCurrency(),
                paid.stream().filter(item -> "DONATION".equals(item.type())).count(),
                paid.stream().filter(item -> "PRODUCT".equals(item.type())).count(),
                bindings.size(),
                transactions
        );
    }

    public MemberPlatform parseProvider(String value) throws BusinessException {
        return MemberPlatform.parse(value)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unsupported member platform"));
    }

    private MemberTransaction toEntity(
            MemberPlatform provider,
            ConfigRow config,
            PlatformTransaction transaction
    ) throws BusinessException {
        if (transaction.transactionId() == null
                || transaction.transactionId().isBlank()
                || transaction.externalUserId() == null
                || transaction.externalUserId().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    provider.name() + " returned an incomplete transaction");
        }
        BigDecimal amount = transaction.amount() == null
                ? BigDecimal.ZERO
                : transaction.amount();
        String currency = transaction.currency() == null
                || transaction.currency().isBlank()
                ? config.currency()
                : exchangeRateService.validateCurrency(transaction.currency());
        BigDecimal amountBase = convertWithFallback(amount, currency, config);
        String baseCurrency = exchangeRateService.baseCurrency();
        BigDecimal amountCny;
        if ("CNY".equals(currency)) {
            amountCny = amount;
        } else if ("CNY".equals(baseCurrency)) {
            amountCny = amountBase;
        } else {
            amountCny = exchangeRateService.convert(amount, "CNY");
        }
        String status = transaction.status() == null
                || transaction.status().isBlank()
                ? "COMPLETED"
                : transaction.status();
        try {
            return new MemberTransaction()
                    .setProvider(provider.name())
                    .setExternalTransactionId(transaction.transactionId().trim())
                    .setExternalUserId(transaction.externalUserId().trim())
                    .setExternalUserName(blankToNull(transaction.externalUserName()))
                    .setTransactionType(transaction.type())
                    .setSku(blankToNull(transaction.sku()))
                    .setProductName(blankToNull(transaction.productName()))
                    .setAmount(amount)
                    .setCurrency(currency)
                    .setAmountCny(amountCny)
                    .setAmountBase(amountBase)
                    .setBaseCurrency(baseCurrency)
                    .setTransactionStatus(status)
                    .setOccurredAt(transaction.occurredAt())
                    .setRawData(transaction.raw() == null
                            ? null
                            : objectMapper.writeValueAsString(transaction.raw()))
                    .setSyncedAt(OffsetDateTime.now())
                    .setLicenseConsumed(false);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to store " + provider.name() + " transaction: "
                            + exception.getMessage());
        }
    }

    private TransactionRow transactionByProof(
            MemberPlatform provider,
            String proof
    ) {
        String normalized = proof.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        MemberTransaction entity = transactionMapper.selectOne(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", provider.name())
                        .eq("remote_present", true)
                        .and(query -> query
                                .eq("external_transaction_id", normalized)
                                .or()
                                .likeRight("external_transaction_id", normalized + ":")
                                .or()
                                .eq("external_user_id", lower)
                                .or()
                                .eq("external_user_name", normalized))
                        .orderByDesc("occurred_at")
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return null;
        }
        return new TransactionRow(
                entity.getExternalUserId(),
                entity.getExternalUserName(),
                entity.getTransactionStatus()
        );
    }

    private List<BindingRow> bindings(long userId, MemberPlatform provider) {
        return bindingMapper.selectList(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", provider.name())
                        .eq("btn_user_id", userId)
                        .orderByAsc("bound_at")
        ).stream().map(entity -> new BindingRow(
                entity.getExternalUserId(),
                entity.getExternalUserName(),
                entity.getExternalUserAvatar(),
                entity.getProof(),
                entity.getBoundAt(),
                entity.getLastSyncedAt(),
                entity.getLastError()
        )).toList();
    }

    private List<TransactionView> transactions(
            MemberPlatform provider,
            List<String> externalUserIds
    ) {
        if (externalUserIds.isEmpty()) {
            return List.of();
        }
        return transactionMapper.selectList(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", provider.name())
                        .in("external_user_id", externalUserIds)
                        .orderByDesc("occurred_at")
                        .orderByDesc("external_transaction_id")
        ).stream().map(entity -> new TransactionView(
                entity.getExternalTransactionId(),
                entity.getTransactionType(),
                entity.getSku(),
                entity.getProductName(),
                entity.getAmount().toPlainString(),
                entity.getCurrency(),
                entity.getAmountBase() == null
                        ? "0.00"
                        : entity.getAmountBase().toPlainString(),
                entity.getAmountBase() == null
                        ? "0.00"
                        : entity.getAmountBase().toPlainString(),
                entity.getBaseCurrency() == null
                        ? exchangeRateService.baseCurrency()
                        : entity.getBaseCurrency(),
                entity.getTransactionStatus(),
                !Boolean.FALSE.equals(entity.getRemotePresent()),
                entity.getOccurredAt()
        )).toList();
    }

    private ProviderView providerView(MemberPlatform provider) {
        MemberPlatformAdapter adapter = adapter(provider);
        MemberPlatformConfig entity = configMapper.selectById(provider.name());
        ConfigRow config = entity == null
                ? new ConfigRow(
                false,
                null,
                null,
                null,
                adapter == null
                        ? exchangeRateService.baseCurrency()
                        : adapter.defaultCurrency(),
                BigDecimal.ONE
        )
                : mapConfig(entity);
        long bindings = bindingMapper.selectCount(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", provider.name())
        );
        long transactions = transactionMapper.selectCount(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", provider.name())
        );
        MemberTransaction latest = transactionMapper.selectOne(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", provider.name())
                        .orderByDesc("synced_at")
                        .last("LIMIT 1")
        );
        OffsetDateTime lastSyncedAt = latest == null ? null : latest.getSyncedAt();
        MemberPlatformSyncRun run = syncRunMapper.selectOne(
                new QueryWrapper<MemberPlatformSyncRun>()
                        .eq("provider", provider.name())
                        .orderByDesc("started_at")
                        .last("LIMIT 1")
        );
        return new ProviderView(
                provider.name(),
                adapter != null,
                Boolean.TRUE.equals(config.enabled()),
                adapter != null && adapter.supportsHistorySync(),
                adapter != null && adapter.supportsWebhook(),
                adapter == null ? null : adapter.documentationUrl(),
                adapter == null ? List.of() : adapter.configFields(),
                config.accountId(),
                config.accessToken() != null && !config.accessToken().isBlank(),
                config.webhookToken() != null && !config.webhookToken().isBlank(),
                adapter != null && ready(adapter, config),
                config.currency(),
                config.exchangeRate().toPlainString(),
                bindings,
                transactions,
                lastSyncedAt,
                run == null ? null : run.getStatus(),
                run == null ? null : run.getStartedAt(),
                run == null ? null : run.getErrorMessage()
        );
    }

    private ConfigRow config(MemberPlatform provider) {
        MemberPlatformConfig entity = configMapper.selectById(provider.name());
        return entity == null ? null : mapConfig(entity);
    }

    private ConfigRow mapConfig(MemberPlatformConfig entity) {
        return new ConfigRow(
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getAccountId(),
                entity.getAccessToken(),
                entity.getWebhookToken(),
                entity.getCurrency(),
                entity.getExchangeRate()
        );
    }

    private MemberPlatformCredentials credentials(
            MemberPlatformAdapter adapter,
            ConfigRow config
    ) {
        return new MemberPlatformCredentials(
                adapter.provider(),
                config.accessToken(),
                config.accountId(),
                config.webhookToken(),
                config.currency() == null || config.currency().isBlank()
                        ? adapter.defaultCurrency()
                        : config.currency(),
                config.exchangeRate() == null
                        ? BigDecimal.ONE
                        : config.exchangeRate()
        );
    }

    private boolean ready(
            MemberPlatformAdapter adapter,
            @Nullable ConfigRow config
    ) {
        if (adapter == null || config == null || !config.enabled()) {
            return false;
        }
        for (String field : adapter.requiredConfigFields()) {
            String value = switch (field) {
                case "accessToken" -> config.accessToken();
                case "accountId" -> config.accountId();
                case "webhookToken" -> config.webhookToken();
                default -> null;
            };
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private MemberPlatformAdapter adapter(@Nullable MemberPlatform provider) {
        if (provider == null) {
            return null;
        }
        return adapters.stream()
                .filter(candidate -> candidate.provider() == provider)
                .findFirst()
                .orElse(null);
    }

    private MemberPlatformAdapter requireAdapter(MemberPlatform provider)
            throws BusinessException {
        MemberPlatformAdapter adapter = adapter(provider);
        if (adapter == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    provider.name()
                            + " does not provide a public sales API; it cannot be integrated");
        }
        return adapter;
    }

    private boolean afdianConfigured() {
        return afdianSettingsService.configured();
    }

    private boolean completed(String status) {
        return status != null && COMPLETED_STATUSES.contains(
                status.trim().toUpperCase(Locale.ROOT)
        );
    }

    private BigDecimal convertWithFallback(
            BigDecimal amount,
            String currency,
            ConfigRow config
    ) throws BusinessException {
        try {
            return exchangeRateService.convert(amount, currency);
        } catch (BusinessException exception) {
            if (currency.equalsIgnoreCase(config.currency())
                    && config.exchangeRate() != null
                    && config.exchangeRate().signum() > 0) {
                return amount.multiply(config.exchangeRate())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }
            throw exception;
        }
    }

    private String validatedText(
            @Nullable String value,
            int maxLength,
            String errorMessage
    ) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private void markBindingsSynced(MemberPlatform provider) {
        bindingMapper.update(
                null,
                new LambdaUpdateWrapper<MemberPlatformBinding>()
                        .set(MemberPlatformBinding::getLastSyncedAt, OffsetDateTime.now())
                        .set(MemberPlatformBinding::getLastError, null)
                        .eq(MemberPlatformBinding::getProvider, provider.name())
        );
    }

    private void markBindingsFailed(MemberPlatform provider, String message) {
        bindingMapper.update(
                null,
                new LambdaUpdateWrapper<MemberPlatformBinding>()
                        .set(MemberPlatformBinding::getLastError,
                                message == null
                                        ? provider.name() + " synchronization failed"
                                        : message)
                        .eq(MemberPlatformBinding::getProvider, provider.name())
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ConfigRow(
            boolean enabled,
            String accountId,
            String accessToken,
            String webhookToken,
            String currency,
            BigDecimal exchangeRate
    ) {
    }

    private record TransactionRow(
            String externalUserId,
            String externalUserName,
            String status
    ) {
    }

    private record BindingRow(
            String externalUserId,
            String externalUserName,
            String externalUserAvatar,
            String proof,
            OffsetDateTime boundAt,
            OffsetDateTime lastSyncedAt,
            String lastError
    ) {
    }
}
