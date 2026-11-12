package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.ProviderUpdateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.ProviderView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MemberPlatformPublicModels.ProviderBindingView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MemberPlatformPublicModels.TransactionView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.entity.MemberPlatformBinding;
import com.ghostchu.btn.sparkle.entity.MemberPlatformConfig;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformConfigMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
    private static final List<MemberPlatform> GENERIC_PROVIDERS = List.of(
            MemberPlatform.PATREON,
            MemberPlatform.KOFI,
            MemberPlatform.MIANBAODUO,
            MemberPlatform.LIANDONG
    );
    private static final List<String> TRANSACTION_ARRAY_FIELDS = List.of(
            "transactions",
            "orders",
            "records",
            "items",
            "payments",
            "charges",
            "members"
    );
    private static final int PAGE_SIZE = 100;
    private static final int MAX_SYNC_PAGES = 500;

    private final MemberPlatformConfigMapper configMapper;
    private final MemberPlatformBindingMapper bindingMapper;
    private final MemberTransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;
    private final ExchangeRateService exchangeRateService;
    private final AfdianSettingsService afdianSettingsService;
    private final MemberTransactionLedgerService ledgerService;
    private final Map<MemberPlatform, ReentrantLock> syncLocks =
            new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SettingsView settings() {
        return new SettingsView(
                GENERIC_PROVIDERS.stream()
                        .map(provider -> providerView(provider))
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
        requireGeneric(provider);
        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Member platform configuration is required");
        }
        String apiUrl = validateOptionalUrl(request.apiUrl());
        String currency = exchangeRateService.validateCurrency(
                request.currency() == null || request.currency().isBlank()
                        ? "CNY"
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
        String accessToken = request.accessToken();
        boolean clearAccessToken = Boolean.TRUE.equals(request.clearAccessToken());
        String authHeader = validateAuthHeader(request.authHeader());
        String authScheme = validateAuthScheme(request.authScheme());
        String webhookToken = request.webhookToken();
        boolean clearWebhookToken = Boolean.TRUE.equals(request.clearWebhookToken());
        MemberPlatformConfig config = configMapper.selectById(provider.name());
        boolean insert = config == null;
        if (insert) {
            config = new MemberPlatformConfig()
                    .setProvider(provider.name());
        }
        config.setEnabled(request.enabled())
                .setApiUrl(blankToNull(apiUrl))
                .setAccountId(blankToNull(request.accountId()))
                .setAuthHeader(authHeader)
                .setAuthScheme(authScheme)
                .setCurrency(currency)
                .setExchangeRate(exchangeRate)
                .setUpdatedAt(OffsetDateTime.now())
                .setUpdatedBy(operatorId);
        if (clearAccessToken) {
            config.setAccessToken(null);
        } else if (accessToken != null && !accessToken.isBlank()) {
            if (accessToken.length() > 4_096) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Member platform access credential is too long");
            }
            config.setAccessToken(accessToken.trim());
        }
        if (clearWebhookToken) {
            config.setWebhookToken(null);
        } else if (webhookToken != null && !webhookToken.isBlank()) {
            if (webhookToken.length() > 4_096) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Member platform webhook token is too long");
            }
            config.setWebhookToken(webhookToken.trim());
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
        for (MemberPlatform provider : GENERIC_PROVIDERS) {
            values.add(bindingView(userId, provider, true));
        }
        return values.stream()
                .filter(ProviderBindingView::configured)
                .toList();
    }

    public boolean anyConfigured() {
        return configMapper.selectList(
                new QueryWrapper<MemberPlatformConfig>().eq("enabled", true)
        ).stream().anyMatch(entity -> integrationReady(mapConfig(entity)));
    }

    public ProviderBindingView bind(
            long userId,
            MemberPlatform provider,
            String proof
    ) throws BusinessException {
        requireGeneric(provider);
        ConfigRow config = config(provider);
        if (config == null || !integrationReady(config)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.name() + " integration is not configured");
        }
        String normalizedProof = proof == null ? "" : proof.trim();
        if (normalizedProof.isBlank() || normalizedProof.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "A valid order or transaction proof is required");
        }
        TransactionRow transaction = transactionByProof(provider, normalizedProof);
        if (transaction == null) {
            syncProvider(provider, config);
            transaction = transactionByProof(provider, normalizedProof);
        }
        if (transaction == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "The transaction was not found in the cached " + provider.name() + " history");
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
        requireGeneric(provider);
        if (bindingMapper.delete(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", provider.name())
                        .eq("btn_user_id", userId)
        ) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "No " + provider.name() + " account is linked");
        }
    }

    public void ingestWebhook(
            MemberPlatform provider,
            String token,
            JsonNode payload
    ) throws BusinessException {
        requireGeneric(provider);
        ConfigRow config = config(provider);
        if (config == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.name() + " integration is not configured");
        }
        String effectiveToken = token;
        if ((effectiveToken == null || effectiveToken.isBlank())
                && provider == MemberPlatform.KOFI) {
            effectiveToken = text(payload, "verification_token", "verificationToken");
        }
        if (!constantEquals(config.webhookToken(), effectiveToken)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Invalid integration token");
        }
        ingestPayload(provider, config, payload);
    }

    private boolean syncProvider(MemberPlatform provider, @Nullable ConfigRow config)
            throws BusinessException {
        requireGeneric(provider);
        ConfigRow resolved = config == null ? config(provider) : config;
        if (resolved == null || !integrationReady(resolved)
                || resolved.apiUrl() == null || resolved.apiUrl().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.name() + " API URL is not configured");
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
            return synchronizeProviderSnapshot(provider, resolved);
        } finally {
            lock.unlock();
        }
    }

    private boolean synchronizeProviderSnapshot(
            MemberPlatform provider,
            ConfigRow config
    ) throws BusinessException {
        MemberTransactionLedgerService.SyncSession session =
                ledgerService.begin(provider);
        boolean paginated = config.apiUrl().contains("{page}");
        int page = 1;
        Set<String> seenTransactions = new java.util.HashSet<>();
        try {
            while (page <= MAX_SYNC_PAGES) {
                JsonNode root = fetchProviderPage(provider, config, page);
                ledgerService.recordPage(session);
                List<JsonNode> records = transactionNodes(provider, root);
                if (records.isEmpty()) {
                    break;
                }
                int accepted = 0;
                for (JsonNode node : records) {
                    String identity = transactionIdentity(node);
                    if (identity.isBlank()) {
                        throw new BusinessException(HttpStatus.BAD_GATEWAY,
                                provider.name()
                                        + " returned a transaction without an ID");
                    }
                    if (!seenTransactions.add(identity)) {
                        continue;
                    }
                    if (!ingestNode(provider, config, node, session)) {
                        throw new BusinessException(HttpStatus.BAD_GATEWAY,
                                provider.name()
                                        + " returned an incomplete transaction");
                    }
                    accepted++;
                }
                int nextPage = nextPage(root, page);
                if (!paginated || nextPage <= page) {
                    break;
                }
                if (accepted == 0) {
                    String reason = provider.name()
                            + " pagination repeated a page without new transactions";
                    ledgerService.incomplete(
                            session,
                            reason
                    );
                    markBindingsFailed(provider, reason);
                    return false;
                }
                page = nextPage;
            }
            if (page > MAX_SYNC_PAGES) {
                String reason = provider.name() + " pagination exceeded the "
                        + MAX_SYNC_PAGES + " page safety limit";
                ledgerService.incomplete(
                        session,
                        reason
                );
                markBindingsFailed(provider, reason);
                return false;
            }
            ledgerService.complete(session);
            markBindingsSynced(provider);
            return true;
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

    private JsonNode fetchProviderPage(
            MemberPlatform provider,
            ConfigRow config,
            int page
    ) throws BusinessException {
        HttpRequest.Builder builder;
        try {
            String url = config.apiUrl()
                    .replace("{accountId}", encode(config.accountId()))
                    .replace("{page}", Integer.toString(page))
                    .replace("{perPage}", Integer.toString(PAGE_SIZE));
            URI uri = URI.create(url);
            builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SparkleBTN-MemberPlatforms");
            if (config.accessToken() != null && !config.accessToken().isBlank()) {
                String credential = config.authScheme() == null
                        || config.authScheme().isBlank()
                        ? config.accessToken()
                        : config.authScheme() + " " + config.accessToken();
                builder.header(
                        config.authHeader() == null
                                || config.authHeader().isBlank()
                                ? "Authorization"
                                : config.authHeader(),
                        credential
                );
            }
            if (config.accountId() != null && !config.accountId().isBlank()) {
                builder.header("X-Account-Id", config.accountId());
            }
            builder.GET();
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    provider.name() + " API URL is invalid");
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to sync " + provider.name() + ": " + exception.getMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    provider.name() + " API returned HTTP " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Invalid " + provider.name() + " API response");
        }
    }

    private List<JsonNode> transactionNodes(
            MemberPlatform provider,
            JsonNode root
    ) throws BusinessException {
        if (root.isArray()) {
            return iterable(root);
        }
        for (String field : TRANSACTION_ARRAY_FIELDS) {
            if (root.path(field).isArray()) {
                return iterable(root.path(field));
            }
        }
        if (root.path("data").isArray()) {
            return iterable(root.path("data"));
        }
        for (String container : List.of("data", "result", "response")) {
            JsonNode nested = root.path(container);
            for (String field : TRANSACTION_ARRAY_FIELDS) {
                if (nested.path(field).isArray()) {
                    return iterable(nested.path(field));
                }
            }
        }
        JsonNode data = root.path("data");
        if (data.isObject()) {
            return List.of(data);
        }
        if (root.isObject() && looksLikeTransaction(root)) {
            return List.of(root);
        }
        if (root.isObject() && root.isEmpty()) {
            return List.of();
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST,
                provider.name() + " response must contain transactions");
    }

    private int nextPage(JsonNode root, int currentPage) {
        JsonNode metadata = root.path("pagination");
        int next = metadata.path("next_page").asInt(
                root.path("next_page").asInt(
                        root.path("meta").path("next_page").asInt(0)
                )
        );
        if (next > currentPage) {
            return next;
        }
        JsonNode hasMoreNode = metadata.path("has_more");
        if (hasMoreNode.isMissingNode()) {
            hasMoreNode = root.path("has_more");
        }
        if (hasMoreNode.isMissingNode()) {
            hasMoreNode = root.path("meta").path("pagination").path("has_more");
        }
        if (hasMoreNode.isBoolean() && !hasMoreNode.booleanValue()) {
            return 0;
        }
        int total = metadata.path("total").asInt(
                root.path("total").asInt(
                        root.path("meta").path("total").asInt(0)
                )
        );
        int perPage = metadata.path("per_page").asInt(PAGE_SIZE);
        if (total > 0 && currentPage * perPage >= total) {
            return 0;
        }
        return currentPage + 1;
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
        for (MemberPlatform provider : GENERIC_PROVIDERS) {
            ConfigRow config = config(provider);
            if (config == null || !integrationReady(config)
                    || config.apiUrl() == null || config.apiUrl().isBlank()) {
                continue;
            }
            try {
                if (syncProvider(provider, config)) {
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
        ConfigRow config = generic ? config(provider) : null;
        boolean configured = generic
                ? config != null && integrationReady(config)
                : afdianConfigured();
        BindingRow binding = binding(userId, provider);
        if (binding == null) {
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
                    List.of()
            );
        }
        List<TransactionView> transactions = transactions(provider, binding.externalUserId());
        BigDecimal total = transactions.stream()
                .filter(item -> item.remotePresent() && "DONATION".equals(item.type()))
                .map(item -> new BigDecimal(item.amountBase()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCny = transactions.stream()
                .filter(item -> item.remotePresent() && "DONATION".equals(item.type()))
                .map(item -> new BigDecimal(item.amountCny()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProviderBindingView(
                configured,
                true,
                provider.name(),
                binding.externalUserId(),
                binding.externalUserName(),
                binding.externalUserAvatar(),
                binding.proof(),
                binding.boundAt(),
                binding.lastSyncedAt(),
                binding.lastError(),
                totalCny.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                exchangeRateService.baseCurrency(),
                transactions.stream()
                        .filter(item -> item.remotePresent()
                                && "DONATION".equals(item.type()))
                        .count(),
                transactions.stream()
                        .filter(item -> item.remotePresent()
                                && "PRODUCT".equals(item.type()))
                        .count(),
                transactions
        );
    }

    public MemberPlatform parseProvider(String value) throws BusinessException {
        return MemberPlatform.parse(value)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unsupported member platform"));
    }

    private void ingestPayload(
            MemberPlatform provider,
            ConfigRow config,
            JsonNode root
    ) throws BusinessException {
        for (JsonNode node : transactionNodes(provider, root)) {
            ingestNode(provider, config, node, null);
        }
    }

    private boolean ingestNode(
            MemberPlatform provider,
            ConfigRow config,
            JsonNode node,
            @Nullable MemberTransactionLedgerService.SyncSession session
    ) throws BusinessException {
        if (provider != MemberPlatform.KOFI) {
            return upsertCanonical(provider, config, node, session);
        }
        JsonNode items = node.path("shop_items");
        if (!items.isArray() || items.isEmpty()) {
            return upsertCanonical(provider, config, node, session);
        }
        String baseId = transactionIdentity(node);
        if (baseId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Ko-fi shop order is missing its transaction ID");
        }
        BigDecimal orderAmount = decimal(node, "amount", "total_amount", "totalAmount");
        String currency = text(node, "currency");
        boolean accepted = false;
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String sku = text(item,
                    "direct_link_code", "directLinkCode", "item_id", "itemId", "id");
            ObjectNode product = node.deepCopy();
            product.put(
                    "transactionId",
                    baseId + ":"
                            + (sku.isBlank() ? "item" : sku)
                            + ":" + (index + 1)
            );
            product.put("type", "PRODUCT");
            if (!sku.isBlank()) {
                product.put("sku", sku);
            }
            String name = text(item, "variation_name", "variationName",
                    "product_name", "productName", "name");
            if (!name.isBlank()) {
                product.put("productName", name);
            }
            BigDecimal itemAmount = decimal(item,
                    "item_total", "itemTotal", "amount", "price");
            if (itemAmount.signum() == 0 && index == 0) {
                itemAmount = orderAmount;
            }
            product.set("amount", DecimalNode.valueOf(itemAmount));
            if (!currency.isBlank()) {
                product.put("currency", currency);
            }
            accepted |= upsertCanonical(provider, config, product, session);
        }
        return accepted;
    }

    private boolean upsertCanonical(
            MemberPlatform provider,
            ConfigRow config,
            JsonNode node,
            @Nullable MemberTransactionLedgerService.SyncSession session
    ) throws BusinessException {
        String transactionId = text(node,
                "transactionId", "transaction_id", "orderId", "order_id",
                "out_trade_no", "kofi_transaction_id", "id");
        String externalUserId = text(node,
                "externalUserId", "external_user_id", "userId", "user_id",
                "patronId", "patron_id", "buyerId", "buyer_id", "email");
        if (externalUserId.isBlank()) {
            externalUserId = relationshipId(node, "user", "patron");
        }
        if (externalUserId.isBlank() && provider == MemberPlatform.KOFI) {
            externalUserId = text(node, "from_email", "fromEmail");
        }
        if (transactionId.isBlank() || externalUserId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    provider.name()
                            + " transaction is missing its transaction or user ID");
        }
        String externalUserName = text(node,
                "externalUserName", "userName", "user_name", "from_name",
                "fromName", "patronName", "buyerName", "name");
        String sku = text(node, "sku", "planId", "plan_id", "tierId",
                "tier_id", "productId", "product_id", "direct_link_code");
        if (sku.isBlank()) {
            sku = relationshipId(node, "currently_entitled_tier", "tier");
        }
        String productName = text(node, "productName", "product_name",
                "planTitle", "plan_title", "tierName", "tier_name",
                "variation_name");
        String type = text(node, "type", "kind").toUpperCase(Locale.ROOT);
        if (!"PRODUCT".equals(type) && !"DONATION".equals(type)) {
            if ((provider == MemberPlatform.PATREON || provider == MemberPlatform.KOFI)
                    && (type.contains("MEMBER") || type.contains("SUBSCRIPTION")
                    || type.contains("PLEDGE"))) {
                type = "DONATION";
            } else if (type.contains("SHOP") || type.contains("PRODUCT")
                    || type.contains("ORDER") && !type.contains("DONATION")) {
                type = "PRODUCT";
            } else {
                type = sku.isBlank() ? "DONATION" : "PRODUCT";
            }
        }
        BigDecimal amount = decimal(node,
                "amount", "total_amount", "totalAmount", "show_amount", "paid");
        if (amount.signum() == 0) {
            amount = centsDecimal(node,
                    "currently_entitled_amount_cents",
                    "amount_cents",
                    "total_amount_cents");
        }
        String currency = text(node, "currency");
        if (currency.isBlank()) {
            currency = config.currency() == null ? "CNY" : config.currency();
        }
        currency = exchangeRateService.validateCurrency(currency);
        BigDecimal amountBase;
        if (node.path("amountBase").isNumber()) {
            amountBase = node.path("amountBase").decimalValue();
        } else if (node.path("amount_base").isNumber()) {
            amountBase = node.path("amount_base").decimalValue();
        } else {
            amountBase = convertWithFallback(amount, currency, config);
        }
        String baseCurrency = exchangeRateService.baseCurrency();
        BigDecimal amountCny;
        if ("CNY".equals(currency)) {
            amountCny = amount;
        } else if ("CNY".equals(baseCurrency)) {
            amountCny = amountBase;
        } else {
            amountCny = exchangeRateService.convert(amount, "CNY");
        }
        String status = text(node,
                "status", "state", "orderStatus", "patron_status",
                "payment_status");
        if (status.isBlank()) {
            status = "COMPLETED";
        }
        OffsetDateTime occurredAt = time(node);
        try {
            MemberTransaction entity = new MemberTransaction()
                    .setProvider(provider.name())
                    .setExternalTransactionId(transactionId)
                    .setExternalUserId(externalUserId)
                    .setExternalUserName(blankToNull(externalUserName))
                    .setTransactionType(type)
                    .setSku(blankToNull(sku))
                    .setProductName(blankToNull(productName))
                    .setAmount(amount)
                    .setCurrency(currency)
                    .setAmountCny(amountCny)
                    .setAmountBase(amountBase)
                    .setBaseCurrency(baseCurrency)
                    .setTransactionStatus(status)
                    .setOccurredAt(occurredAt)
                    .setRawData(objectMapper.writeValueAsString(node))
                    .setSyncedAt(OffsetDateTime.now())
                    .setLicenseConsumed(false);
            if (session == null) {
                ledgerService.applyWebhook(entity);
            } else {
                ledgerService.apply(session, entity);
            }
            return true;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to store " + provider.name() + " transaction: "
                            + exception.getMessage());
        }
    }

    private String transactionIdentity(JsonNode node) {
        return text(node,
                "transactionId", "transaction_id", "orderId", "order_id",
                "out_trade_no", "kofi_transaction_id", "id");
    }

    private boolean looksLikeTransaction(JsonNode node) {
        return !transactionIdentity(node).isBlank()
                && (!text(node, "externalUserId", "user_id", "email",
                "from_email", "from_name").isBlank()
                || !relationshipId(node, "user", "patron").isBlank());
    }

    private List<JsonNode> iterable(JsonNode array) {
        List<JsonNode> values = new ArrayList<>(array.size());
        array.forEach(values::add);
        return values;
    }

    private TransactionRow transactionByProof(
            MemberPlatform provider,
            String transactionId
    ) {
        MemberTransaction entity = transactionMapper.selectOne(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", provider.name())
                        .eq("external_transaction_id", transactionId)
                        .eq("remote_present", true)
        );
        return entity == null ? null : new TransactionRow(
                entity.getExternalTransactionId(),
                entity.getExternalUserId(),
                entity.getExternalUserName(),
                entity.getTransactionStatus()
        );
    }

    private BindingRow binding(long userId, MemberPlatform provider) {
        MemberPlatformBinding entity = bindingMapper.selectOne(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", provider.name())
                        .eq("btn_user_id", userId)
        );
        return entity == null ? null : new BindingRow(
                provider,
                entity.getExternalUserId(),
                entity.getExternalUserName(),
                entity.getExternalUserAvatar(),
                entity.getProof(),
                entity.getBoundAt(),
                entity.getLastSyncedAt(),
                entity.getLastError()
        );
    }

    private List<TransactionView> transactions(
            MemberPlatform provider,
            String externalUserId
    ) {
        return transactionMapper.selectList(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", provider.name())
                        .eq("external_user_id", externalUserId)
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
        MemberPlatformConfig entity = configMapper.selectById(provider.name());
        ConfigRow config = entity == null
                ? new ConfigRow(provider, false, null, null, null,
                "Authorization", "Bearer", null,
                exchangeRateService.baseCurrency(), BigDecimal.ONE)
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
        return new ProviderView(
                provider.name(),
                config.enabled(),
                config.apiUrl(),
                config.accountId(),
                config.accessToken() != null && !config.accessToken().isBlank(),
                config.authHeader(),
                config.authScheme(),
                config.webhookToken() != null && !config.webhookToken().isBlank(),
                config.currency(),
                config.exchangeRate().toPlainString(),
                bindings,
                transactions,
                lastSyncedAt
        );
    }

    private ConfigRow config(MemberPlatform provider) {
        MemberPlatformConfig entity = configMapper.selectById(provider.name());
        return entity == null ? null : mapConfig(entity);
    }

    private ConfigRow mapConfig(MemberPlatformConfig entity) {
        return new ConfigRow(
                MemberPlatform.parse(entity.getProvider())
                        .orElse(MemberPlatform.PATREON),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getApiUrl(),
                entity.getAccountId(),
                entity.getAccessToken(),
                entity.getAuthHeader() == null || entity.getAuthHeader().isBlank()
                        ? "Authorization"
                        : entity.getAuthHeader(),
                entity.getAuthScheme() == null
                        ? "Bearer"
                        : entity.getAuthScheme(),
                entity.getWebhookToken(),
                entity.getCurrency(),
                entity.getExchangeRate()
        );
    }

    private boolean afdianConfigured() {
        return afdianSettingsService.configured();
    }

    private void requireGeneric(MemberPlatform provider) throws BusinessException {
        if (provider == MemberPlatform.AFDIAN) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian uses its dedicated integration");
        }
    }

    private String validateOptionalUrl(String value) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(normalized.replace("{accountId}", "account"));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Member platform API URL is invalid");
        }
        if (uri.getScheme() == null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Member platform API URL must be HTTP(S)");
        }
        return normalized;
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

    private BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            for (JsonNode candidate : nodes(node)) {
                JsonNode value = candidate.path(field);
                if (value.isNumber()) {
                    return value.decimalValue();
                }
                String text = value.asText("");
                if (!text.isBlank()) {
                    try {
                        return new BigDecimal(text);
                    } catch (NumberFormatException ignored) {
                        // Try the next alias.
                    }
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal centsDecimal(JsonNode node, String... fields) {
        BigDecimal cents = decimal(node, fields);
        return cents.divide(
                new BigDecimal("100"),
                2,
                java.math.RoundingMode.HALF_UP
        );
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            for (JsonNode candidate : nodes(node)) {
                String value = candidate.path(field).asText("");
                if (!value.isBlank()) return value.trim();
            }
        }
        return "";
    }

    private OffsetDateTime time(JsonNode node) {
        for (String field : List.of(
                "occurredAt", "occurred_at", "createdAt", "created_at",
                "create_time", "timestamp", "pledge_relationship_start",
                "last_charge_date", "signed_up_at", "created"
        )) {
            for (JsonNode candidate : nodes(node)) {
                JsonNode value = candidate.path(field);
                if (value.isMissingNode() || value.isNull()) continue;
                if (value.isNumber()) {
                    long timestamp = value.asLong();
                    if (timestamp > 10_000_000_000L) {
                        return OffsetDateTime.ofInstant(
                                Instant.ofEpochMilli(timestamp),
                                ZoneOffset.UTC
                        );
                    }
                    return OffsetDateTime.ofInstant(
                            Instant.ofEpochSecond(timestamp),
                            ZoneOffset.UTC
                    );
                }
                String text = value.asText("");
                if (!text.isBlank()) {
                    try {
                        return OffsetDateTime.parse(text);
                    } catch (Exception ignored) {
                        try {
                            return OffsetDateTime.ofInstant(
                                    Instant.parse(text),
                                    ZoneOffset.UTC
                            );
                        } catch (Exception ignoredAgain) {
                            // Try the next alias.
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<JsonNode> nodes(JsonNode node) {
        List<JsonNode> values = new ArrayList<>(3);
        values.add(node);
        if (node.path("attributes").isObject()) {
            values.add(node.path("attributes"));
        }
        if (node.path("data").isObject()) {
            values.add(node.path("data"));
        }
        return values;
    }

    private String relationshipId(JsonNode node, String... relationships) {
        for (String relationship : relationships) {
            JsonNode data = node.path("relationships")
                    .path(relationship)
                    .path("data");
            if (data.isArray() && !data.isEmpty()) {
                data = data.get(0);
            }
            if (data.isObject()) {
                String id = data.path("id").asText("");
                if (!id.isBlank()) {
                    return id;
                }
            }
        }
        return "";
    }

    private boolean integrationReady(ConfigRow config) {
        return config != null
                && config.enabled()
                && config.apiUrl() != null
                && !config.apiUrl().isBlank()
                && config.accessToken() != null
                && !config.accessToken().isBlank();
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

    private boolean constantEquals(String expected, String provided) {
        if (expected == null || expected.isBlank()) return false;
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided == null
                        ? new byte[0]
                        : provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String validateAuthHeader(String value) throws BusinessException {
        String normalized = value == null || value.isBlank()
                ? "Authorization"
                : value.trim();
        if (!normalized.matches("[A-Za-z0-9-]{1,128}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Credential header name is invalid");
        }
        return normalized;
    }

    private String validateAuthScheme(String value) throws BusinessException {
        String normalized = value == null ? "Bearer" : value.trim();
        if (normalized.length() > 64
                || !normalized.matches("[A-Za-z0-9._-]*")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Credential scheme is invalid");
        }
        return normalized;
    }

    private record ConfigRow(
            MemberPlatform provider,
            boolean enabled,
            String apiUrl,
            String accountId,
            String accessToken,
            String authHeader,
            String authScheme,
            String webhookToken,
            String currency,
            BigDecimal exchangeRate
    ) {
    }

    private record TransactionRow(
            String transactionId,
            String externalUserId,
            String externalUserName,
            String status
    ) {
    }

    private record BindingRow(
            MemberPlatform provider,
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
