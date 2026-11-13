package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.DonationView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.SyncResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.BindingView;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.entity.AfdianBinding;
import com.ghostchu.btn.sparkle.entity.AfdianDonation;
import com.ghostchu.btn.sparkle.entity.MemberPlatformBinding;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AfdianBindingMapper;
import com.ghostchu.btn.sparkle.mapper.AfdianDonationMapper;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import com.ghostchu.btn.sparkle.service.AfdianClient.AfdianOrder;
import com.ghostchu.btn.sparkle.service.AfdianClient.OrderPage;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfdianIntegrationService {
    private static final Pattern ORDER_NUMBER = Pattern.compile("[A-Za-z0-9_-]{4,128}");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_SYNC_PAGES = 500;
    private static final int MAX_BIND_SEARCH_PAGES = 50;
    private static final int COMPLETED = 2;

    private final AfdianBindingMapper bindingMapper;
    private final AfdianDonationMapper donationMapper;
    private final MemberPlatformBindingMapper memberBindingMapper;
    private final MemberTransactionMapper memberTransactionMapper;
    private final IUserService userService;
    private final AfdianSettingsService settingsService;
    private final AfdianClient afdianClient;
    private final ObjectMapper objectMapper;
    private final PbhLicenseService licenseService;
    private final ExchangeRateService exchangeRateService;
    private final MemberTransactionLedgerService ledgerService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SettingsView settings() {
        AfdianDonation latest = donationMapper.selectOne(
                new QueryWrapper<AfdianDonation>()
                        .orderByDesc("synced_at")
                        .last("LIMIT 1")
        );
        OffsetDateTime lastSyncedAt = latest == null ? null : latest.getSyncedAt();
        return new SettingsView(
                settingsService.userId(),
                settingsService.tokenConfigured(),
                settingsService.configured(),
                settingsService.apiBaseUrl(),
                settingsService.syncEnabled(),
                settingsService.donationLicenseAmount().toPlainString(),
                settingsService.licenseValidYears(),
                settingsService.licensePrivateKeyConfigured(),
                exchangeRateService.baseCurrency(),
                lastSyncedAt,
                adminBindings(),
                recentDonations(),
                licenseService.recentLicenses(200)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSettings(
            String userId,
            @Nullable String accessToken,
            boolean clearToken,
            String apiBaseUrl,
            boolean syncEnabled,
            String licenseDonationAmount,
            int licenseValidYears,
            @Nullable String licensePrivateKey,
            boolean clearLicensePrivateKey,
            @Nullable Long operatorId
    ) throws BusinessException {
        settingsService.update(
                userId,
                accessToken,
                clearToken,
                apiBaseUrl,
                syncEnabled,
                licenseDonationAmount,
                licenseValidYears,
                licensePrivateKey,
                clearLicensePrivateKey,
                operatorId
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public BindingView bind(long btnUserId, String orderNumber)
            throws BusinessException {
        String normalizedOrder = orderNumber == null ? "" : orderNumber.trim();
        if (!ORDER_NUMBER.matcher(normalizedOrder).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "A valid Afdian order number is required");
        }
        if (!settingsService.configured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Afdian integration is not configured");
        }
        BindingRow existing = binding(btnUserId);
        if (existing != null) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "An Afdian account is already linked; unbind it first");
        }

        AfdianOrder order = findCachedOrder(normalizedOrder);
        if (order == null || order.status() != COMPLETED) {
            AfdianOrder refreshed = findOrder(normalizedOrder);
            if (refreshed != null) {
                order = refreshed;
            }
        }
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "Afdian order was not found");
        }
        if (order.status() != COMPLETED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Only paid Afdian orders can be used to link an account");
        }
        if (order.afdianUserId().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Afdian order did not include a sponsor account");
        }
        try {
            bindingMapper.insert(new AfdianBinding()
                    .setBtnUserId(btnUserId)
                    .setAfdianUserId(order.afdianUserId())
                    .setAfdianName(blankToNull(order.afdianName()))
                    .setAfdianAvatar(blankToNull(order.afdianAvatar()))
                    .setBoundOrderNo(normalizedOrder));
            memberBindingMapper.insert(new MemberPlatformBinding()
                    .setProvider("AFDIAN")
                    .setBtnUserId(btnUserId)
                    .setExternalUserId(order.afdianUserId())
                    .setExternalUserName(blankToNull(order.afdianName()))
                    .setExternalUserAvatar(blankToNull(order.afdianAvatar()))
                    .setProof(normalizedOrder)
                    .setLastSyncedAt(OffsetDateTime.now()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This Afdian account is already linked to another Sparkle user");
        }
        upsertDonation(order, null);
        BindingRow binding = binding(btnUserId);
        if (binding == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to link Afdian account");
        }
        updateBindingSuccess(btnUserId, order.afdianName(), order.afdianAvatar());
        return publicBinding(btnUserId);
    }

    public void unbind(long btnUserId) throws BusinessException {
        if (bindingMapper.delete(
                new QueryWrapper<AfdianBinding>().eq("btn_user_id", btnUserId)
        ) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND,
                    "No Afdian account is linked");
        }
        memberBindingMapper.delete(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", "AFDIAN")
                        .eq("btn_user_id", btnUserId)
        );
    }

    public BindingView bindingForUser(long btnUserId) {
        return publicBinding(btnUserId);
    }

    public SyncResult syncNow() throws BusinessException {
        return synchronize(true);
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 60_000)
    @com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask(
            id = "afdian-sync",
            group = com.ghostchu.btn.sparkle.service.tasks.TaskGroup.SYNC,
            nameKey = "admin.tasks.items.afdian-sync.name",
            descriptionKey = "admin.tasks.items.afdian-sync.description",
            scheduleType = com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType.FIXED_DELAY,
            intervalMillis = 21_600_000,
            initialDelayMillis = 60_000
    )
    public void scheduledSync() {
        if (!settingsService.syncEnabled()) {
            return;
        }
        try {
            synchronize(false);
        } catch (Exception exception) {
            log.warn("Scheduled Afdian synchronization failed: {}", exception.getMessage());
        }
    }

    private SyncResult synchronize(boolean forced) throws BusinessException {
        if (!settingsService.configured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Afdian integration is not configured");
        }
        if (!forced && !settingsService.syncEnabled()) {
            return new SyncResult(true, 0, 0, 0, 0, "Afdian synchronization is disabled");
        }
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Afdian synchronization is already running");
        }
        MemberTransactionLedgerService.SyncSession session = null;
        try {
            session = ledgerService.begin(MemberPlatform.AFDIAN);
            Set<String> boundUsers = new HashSet<>(
                    bindingMapper.selectList(null).stream()
                            .map(AfdianBinding::getAfdianUserId)
                            .toList()
            );

            int page = 1;
            int pages = 0;
            int ordersSeen = 0;
            int completed = 0;
            int totalCount = Integer.MAX_VALUE;
            boolean snapshotComplete = false;
            Map<String, AfdianOrder> latestDonations = new LinkedHashMap<>();
            while (page <= MAX_SYNC_PAGES && ordersSeen < totalCount) {
                OrderPage orderPage = afdianClient.queryOrders(Map.of(
                        "page", page,
                        "per_page", PAGE_SIZE
                ));
                pages++;
                totalCount = orderPage.totalCount();
                if (orderPage.orders().isEmpty()) {
                    snapshotComplete = true;
                    break;
                }
                for (AfdianOrder order : orderPage.orders()) {
                    ordersSeen++;
                    if (order.outTradeNo().isBlank()
                            || order.afdianUserId().isBlank()) {
                        throw new BusinessException(
                                HttpStatus.BAD_GATEWAY,
                                "Afdian returned an order without an order or user ID"
                        );
                    }
                    upsertDonation(order, session);
                    if (order.status() == COMPLETED) {
                        completed++;
                        latestDonations.put(
                                order.afdianUserId(),
                                order
                        );
                    }
                }
                if (ordersSeen >= totalCount) {
                    snapshotComplete = true;
                    break;
                }
                page++;
            }

            if (!snapshotComplete) {
                String message = "Afdian history pagination did not complete";
                ledgerService.incomplete(session, message);
                markAllBindingsFailed(message);
                return new SyncResult(
                        false,
                        pages,
                        ordersSeen,
                        completed,
                        0,
                        message
                );
            }

            for (String afdianUserId : boundUsers) {
                AfdianOrder order = latestDonations.get(afdianUserId);
                if (order == null) {
                    updateBindingTimestampOnly(afdianUserId);
                } else {
                    updateBindingSuccess(
                            afdianUserId,
                            order.afdianName(),
                            order.afdianAvatar()
                    );
                }
            }
            ledgerService.complete(session);
            int bindingsUpdated = boundUsers.size();
            return new SyncResult(
                    true,
                    pages,
                    ordersSeen,
                    completed,
                    bindingsUpdated,
                    "Afdian orders synchronized"
            );
        } catch (BusinessException exception) {
            if (session != null) {
                ledgerService.fail(session, exception);
            }
            markAllBindingsFailed(exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            if (session != null) {
                ledgerService.fail(session, exception);
            }
            markAllBindingsFailed(exception.getMessage());
            throw exception;
        } finally {
            running.set(false);
        }
    }

    private @Nullable AfdianOrder findCachedOrder(String orderNumber) {
        MemberTransaction canonical = memberTransactionMapper.selectOne(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", MemberPlatform.AFDIAN.name())
                        .eq("external_transaction_id", orderNumber)
        );
        if (canonical != null && Boolean.FALSE.equals(canonical.getRemotePresent())) {
            return null;
        }
        AfdianDonation cached = donationMapper.selectById(orderNumber);
        if (cached == null) {
            return null;
        }
        JsonNode raw = null;
        if (cached.getRawData() != null && !cached.getRawData().isBlank()) {
            try {
                raw = objectMapper.readTree(cached.getRawData());
            } catch (Exception ignored) {
                // The typed columns are sufficient when legacy raw data is malformed.
            }
        }
        int productType = canonical != null
                && "PRODUCT".equals(canonical.getTransactionType())
                ? 1
                : raw == null ? 0 : raw.path("product_type").asInt(0);
        JsonNode skuDetail = raw == null
                ? com.fasterxml.jackson.databind.node.MissingNode.getInstance()
                : raw.path("sku_detail");
        return new AfdianOrder(
                cached.getOutTradeNo(),
                cached.getAfdianUserId(),
                cached.getAfdianUserName(),
                null,
                cached.getPlanId(),
                cached.getPlanTitle(),
                cached.getTotalAmount(),
                cached.getShowAmount(),
                cached.getOrderStatus() == null ? 0 : cached.getOrderStatus(),
                cached.getRemark(),
                cached.getCreatedAt(),
                productType,
                skuDetail,
                raw
        );
    }

    private AfdianOrder findOrder(String orderNumber) throws BusinessException {
        OrderPage direct = afdianClient.queryOrders(Map.of(
                "out_trade_no", orderNumber
        ));
        AfdianOrder matching = direct.orders().stream()
                .filter(order -> orderNumber.equals(order.outTradeNo()))
                .findFirst()
                .orElse(null);
        if (matching != null) {
            return matching;
        }

        int page = 1;
        int totalCount = Integer.MAX_VALUE;
        int scanned = 0;
        while (page <= MAX_BIND_SEARCH_PAGES && scanned < totalCount) {
            OrderPage orderPage = afdianClient.queryOrders(Map.of(
                    "page", page,
                    "per_page", PAGE_SIZE
            ));
            totalCount = orderPage.totalCount();
            if (orderPage.orders().isEmpty()) {
                return null;
            }
            for (AfdianOrder order : orderPage.orders()) {
                if (orderNumber.equals(order.outTradeNo())) {
                    return order;
                }
            }
            scanned += orderPage.orders().size();
            if (orderPage.orders().size() < PAGE_SIZE) {
                return null;
            }
            page++;
        }
        return null;
    }

    private void upsertDonation(
            AfdianOrder order,
            @Nullable MemberTransactionLedgerService.SyncSession session
    ) throws BusinessException {
        try {
            donationMapper.upsert(new AfdianDonation()
                    .setOutTradeNo(order.outTradeNo())
                    .setAfdianUserId(order.afdianUserId())
                    .setAfdianUserName(blankToNull(order.afdianName()))
                    .setPlanId(blankToNull(order.planId()))
                    .setPlanTitle(blankToNull(order.planTitle()))
                    .setTotalAmount(order.totalAmount())
                    .setShowAmount(order.showAmount())
                    .setOrderStatus(order.status())
                    .setRemark(blankToNull(order.remark()))
                    .setCreatedAt(order.createdAt())
                    .setRawData(objectMapper.writeValueAsString(order.raw())));
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to store Afdian order: " + exception.getMessage());
        }
        upsertGenericAfdianOrder(order, session);
    }

    private void upsertGenericAfdianOrder(
            AfdianOrder order,
            @Nullable MemberTransactionLedgerService.SyncSession session
    )
            throws BusinessException {
        boolean product = order.isProduct();
        String sku = product ? order.skuId() : order.planId();
        BigDecimal baseAmount = exchangeRateService.convert(
                order.showAmount(),
                "CNY"
        );
        try {
            MemberTransaction transaction = new MemberTransaction()
                    .setProvider("AFDIAN")
                    .setExternalTransactionId(order.outTradeNo())
                    .setExternalUserId(order.afdianUserId())
                    .setExternalUserName(blankToNull(order.afdianName()))
                    .setTransactionType(product ? "PRODUCT" : "DONATION")
                    .setSku(blankToNull(sku))
                    .setProductName(blankToNull(order.planTitle()))
                    .setAmount(order.totalAmount())
                    .setCurrency("CNY")
                    .setAmountCny(order.showAmount())
                    .setAmountBase(baseAmount)
                    .setBaseCurrency(exchangeRateService.baseCurrency())
                    .setTransactionStatus(String.valueOf(order.status()))
                    .setOccurredAt(order.createdAt())
                    .setRawData(objectMapper.writeValueAsString(order.raw()))
                    .setLicenseConsumed(false);
            if (session == null) {
                ledgerService.applyWebhook(transaction);
            } else {
                ledgerService.apply(session, transaction);
            }
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to store normalized Afdian transaction: "
                            + exception.getMessage());
        }
    }

    private BindingView publicBinding(long btnUserId) {
        BindingRow binding = binding(btnUserId);
        if (binding == null) {
            return new BindingView(
                    settingsService.configured(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "0.00",
                    0,
                    List.of()
            );
        }
        List<MemberTransaction> transactions = memberTransactionMapper.selectList(
                new QueryWrapper<MemberTransaction>()
                        .eq("provider", MemberPlatform.AFDIAN.name())
                        .eq("external_user_id", binding.afdianUserId())
                        .orderByDesc("occurred_at")
                        .orderByDesc("external_transaction_id")
        );
        List<com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.DonationView>
                donations = transactions.stream().map(entity -> new com.ghostchu.btn.sparkle.controller.ui.dto
                .AfdianPublicModels.DonationView(
                entity.getExternalTransactionId(),
                entity.getSku(),
                entity.getProductName(),
                entity.getTransactionType(),
                amount(entity.getAmountCny(), entity.getAmount()),
                entity.getTransactionStatus(),
                !Boolean.FALSE.equals(entity.getRemotePresent()),
                null,
                entity.getOccurredAt()
        )).toList();
        List<MemberTransaction> completedTransactions = transactions.stream()
                .filter(item -> !Boolean.FALSE.equals(item.getRemotePresent()))
                .filter(item -> completed(item.getTransactionStatus()))
                .toList();
        // Cumulative payments cover both donations and product SKU orders.
        // License slots are still separated by transaction type elsewhere.
        BigDecimal total = completedTransactions.stream()
                .map(item -> item.getAmountCny() != null
                        ? item.getAmountCny()
                        : item.getAmount())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BindingView(
                settingsService.configured(),
                true,
                binding.afdianUserId(),
                binding.afdianName(),
                binding.afdianAvatar(),
                binding.boundAt(),
                binding.lastSyncedAt(),
                total.toPlainString(),
                completedTransactions.size(),
                donations
        );
    }

    private BindingRow binding(long btnUserId) {
        AfdianBinding entity = bindingMapper.selectOne(
                new QueryWrapper<AfdianBinding>().eq("btn_user_id", btnUserId)
        );
        return entity == null ? null : mapBinding(entity);
    }

    private BindingRow mapBinding(AfdianBinding entity) {
        return new BindingRow(
                entity.getId(),
                entity.getBtnUserId(),
                entity.getAfdianUserId(),
                entity.getAfdianName(),
                entity.getAfdianAvatar(),
                entity.getBoundOrderNo(),
                entity.getBoundAt(),
                entity.getUpdatedAt(),
                entity.getLastSyncedAt(),
                entity.getLastError()
        );
    }

    private List<com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.BindingView>
    adminBindings() {
        List<AfdianBinding> bindings = bindingMapper.selectList(
                new QueryWrapper<AfdianBinding>().orderByDesc("bound_at")
        );
        if (bindings.isEmpty()) return List.of();
        Map<Long, com.ghostchu.btn.sparkle.entity.User> users =
                userService.listByIds(bindings.stream()
                                .map(AfdianBinding::getBtnUserId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.ghostchu.btn.sparkle.entity.User::getId,
                                value -> value
                        ));
        return bindings.stream().map(binding -> {
            List<AfdianDonation> donations = donationMapper.selectList(
                    new QueryWrapper<AfdianDonation>()
                            .eq("afdian_user_id", binding.getAfdianUserId())
                            .eq("order_status", COMPLETED)
            );
            BigDecimal total = donations.stream()
                    .map(AfdianDonation::getShowAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var user = users.get(binding.getBtnUserId());
            return new com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.BindingView(
                    binding.getBtnUserId(),
                    user == null ? null : user.getNickname(),
                    binding.getAfdianUserId(),
                    binding.getAfdianName(),
                    binding.getAfdianAvatar(),
                    binding.getBoundOrderNo(),
                    binding.getBoundAt(),
                    binding.getLastSyncedAt(),
                    binding.getLastError(),
                    total.toPlainString(),
                    donations.size()
            );
        }).toList();
    }

    private List<DonationView> recentDonations() {
        return donationMapper.selectList(
                new QueryWrapper<AfdianDonation>()
                        .eq("order_status", COMPLETED)
                        .orderByDesc("created_at")
                        .orderByDesc("out_trade_no")
                        .last("LIMIT 200")
        ).stream().map(entity -> new DonationView(
                entity.getOutTradeNo(),
                entity.getAfdianUserId(),
                entity.getAfdianUserName(),
                entity.getPlanId(),
                entity.getPlanTitle(),
                entity.getTotalAmount().toPlainString(),
                entity.getShowAmount().toPlainString(),
                entity.getOrderStatus(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getSyncedAt()
        )).toList();
    }

    private void updateBindingSuccess(
            long btnUserId,
            @Nullable String name,
            @Nullable String avatar
    ) {
        AfdianBinding binding = bindingMapper.selectOne(
                new QueryWrapper<AfdianBinding>().eq("btn_user_id", btnUserId)
        );
        if (binding != null) {
            if (name != null && !name.isBlank()) binding.setAfdianName(name);
            if (avatar != null && !avatar.isBlank()) binding.setAfdianAvatar(avatar);
            binding.setLastSyncedAt(OffsetDateTime.now())
                    .setLastError(null)
                    .setUpdatedAt(OffsetDateTime.now());
            bindingMapper.updateById(binding);
        }
        MemberPlatformBinding member = memberBindingMapper.selectOne(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", "AFDIAN")
                        .eq("btn_user_id", btnUserId)
        );
        if (member != null) {
            if (name != null && !name.isBlank()) member.setExternalUserName(name);
            if (avatar != null && !avatar.isBlank()) member.setExternalUserAvatar(avatar);
            member.setLastSyncedAt(OffsetDateTime.now()).setLastError(null);
            memberBindingMapper.updateById(member);
        }
    }

    private void updateBindingSuccess(
            String afdianUserId,
            @Nullable String name,
            @Nullable String avatar
    ) {
        AfdianBinding binding = bindingMapper.selectOne(
                new QueryWrapper<AfdianBinding>().eq("afdian_user_id", afdianUserId)
        );
        if (binding != null) {
            if (name != null && !name.isBlank()) binding.setAfdianName(name);
            if (avatar != null && !avatar.isBlank()) binding.setAfdianAvatar(avatar);
            binding.setLastSyncedAt(OffsetDateTime.now())
                    .setLastError(null)
                    .setUpdatedAt(OffsetDateTime.now());
            bindingMapper.updateById(binding);
        }
        MemberPlatformBinding member = memberBindingMapper.selectOne(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", "AFDIAN")
                        .eq("external_user_id", afdianUserId)
        );
        if (member != null) {
            if (name != null && !name.isBlank()) member.setExternalUserName(name);
            if (avatar != null && !avatar.isBlank()) member.setExternalUserAvatar(avatar);
            member.setLastSyncedAt(OffsetDateTime.now()).setLastError(null);
            memberBindingMapper.updateById(member);
        }
    }

    private void updateBindingTimestampOnly(String afdianUserId) {
        AfdianBinding binding = bindingMapper.selectOne(
                new QueryWrapper<AfdianBinding>().eq("afdian_user_id", afdianUserId)
        );
        if (binding != null) {
            binding.setLastSyncedAt(OffsetDateTime.now())
                    .setLastError(null)
                    .setUpdatedAt(OffsetDateTime.now());
            bindingMapper.updateById(binding);
        }
        MemberPlatformBinding member = memberBindingMapper.selectOne(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("provider", "AFDIAN")
                        .eq("external_user_id", afdianUserId)
        );
        if (member != null) {
            member.setLastSyncedAt(OffsetDateTime.now()).setLastError(null);
            memberBindingMapper.updateById(member);
        }
    }

    private void markAllBindingsFailed(String message) {
        String error = message == null ? "Afdian synchronization failed" : message;
        bindingMapper.update(null,
                new LambdaUpdateWrapper<AfdianBinding>()
                        .set(AfdianBinding::getLastError, error)
                        .set(AfdianBinding::getUpdatedAt, OffsetDateTime.now())
        );
        memberBindingMapper.update(null,
                new LambdaUpdateWrapper<MemberPlatformBinding>()
                        .set(MemberPlatformBinding::getLastError, error)
                        .eq(MemberPlatformBinding::getProvider, "AFDIAN")
        );
    }

    private String amount(@Nullable BigDecimal showAmount, @Nullable BigDecimal totalAmount) {
        BigDecimal value = showAmount != null && showAmount.signum() > 0
                ? showAmount
                : totalAmount == null ? BigDecimal.ZERO : totalAmount;
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private boolean completed(String status) {
        return status != null && Set.of(
                "2",
                "PAID",
                "COMPLETED",
                "SUCCEEDED",
                "SUCCESS",
                "ACTIVE"
        ).contains(status.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record BindingRow(
            long id,
            long btnUserId,
            String afdianUserId,
            String afdianName,
            String afdianAvatar,
            String boundOrderNo,
            OffsetDateTime boundAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastSyncedAt,
            String lastError
    ) {
    }
}
