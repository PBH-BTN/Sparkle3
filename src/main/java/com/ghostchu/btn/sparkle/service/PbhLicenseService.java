package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseAvailabilityView;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseView;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.MemberPlatformBinding;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PbhLicenseService {
    private static final String VERIFY_MAGIC = "PeerBanHelper";
    private static final int LICENSE_VERSION = 2;
    private static final String LICENSE_SOURCE = "PBH-BTN ALIS";
    private static final String LICENSE_DESCRIPTION = "PBH Plus 许可证";
    private static final int RSA_BLOCK_SIZE = 117;

    private final MemberPlatformBindingMapper bindingMapper;
    private final MemberTransactionMapper transactionMapper;
    private final PbhLicenseMapper licenseMapper;
    private final IUserService userService;
    private final AfdianSettingsService settingsService;
    private final ExchangeRateService exchangeRateService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public LicenseAvailabilityView availability(long btnUserId) {
        if (bindings(btnUserId).isEmpty()) {
            return new LicenseAvailabilityView(
                    settingsService.licensePrivateKeyConfigured(),
                    0,
                    0,
                    0,
                    settingsService.donationLicenseAmount().toPlainString(),
                    exchangeRateService.baseCurrency(),
                    settingsService.licenseValidYears(),
                    List.of()
            );
        }
        Availability availability = calculateAvailability(btnUserId);
        return new LicenseAvailabilityView(
                settingsService.licensePrivateKeyConfigured(),
                availability.productSlots(),
                availability.donationSlots(),
                availability.totalSlots(),
                settingsService.donationLicenseAmount().toPlainString(),
                exchangeRateService.baseCurrency(),
                settingsService.licenseValidYears(),
                licenses(btnUserId)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public LicenseView generate(long btnUserId) throws BusinessException {
        if (!settingsService.licensePrivateKeyConfigured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PBHLicense private key is not configured");
        }
        licenseMapper.lockKey("sparkle:pbh-license:" + btnUserId);
        List<MemberPlatformBinding> bindings = bindings(btnUserId);
        if (bindings.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Link a membership platform account before generating a license");
        }
        Availability availability = calculateAvailability(btnUserId);
        if (availability.totalSlots() <= 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "No PBHLicense slots are available");
        }

        User user = userService.getById(btnUserId);
        if (user == null || user.getBannedAt() != null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is unavailable");
        }
        MemberTransaction product = transactionMapper.lockNextAvailableProduct(btnUserId);
        MemberPlatformBinding licenseAccount = product == null
                ? bindings.getFirst()
                : bindings.stream()
                .filter(binding -> binding.getProvider().equals(product.getProvider())
                        && binding.getExternalUserId().equals(product.getExternalUserId()))
                .findFirst()
                .orElse(bindings.getFirst());
        UUID licenseId = UUID.randomUUID();
        String sourceType;
        String sourceOrderNo;
        String planId;
        BigDecimal consumedAmount;
        if (product != null) {
            int updated = transactionMapper.consumeLicenseSlot(product.getId());
            if (updated == 0) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "The selected product order has already been consumed");
            }
            sourceType = "PRODUCT";
            sourceOrderNo = product.getExternalTransactionId();
            planId = product.getSku();
            consumedAmount = product.getAmountBase() == null
                    ? BigDecimal.ZERO : product.getAmountBase();
        } else {
            sourceType = "DONATION";
            sourceOrderNo = null;
            planId = null;
            consumedAmount = settingsService.donationLicenseAmount();
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expireAt = now.plusYears(settingsService.licenseValidYears());
        V2LicensePayload payload = new V2LicensePayload(
                VERIFY_MAGIC,
                LICENSE_VERSION,
                licenseAccount.getProvider().toLowerCase(java.util.Locale.ROOT),
                LICENSE_SOURCE,
                licenseAccount.getExternalUserName() == null
                        || licenseAccount.getExternalUserName().isBlank()
                        ? user.getNickname()
                        : licenseAccount.getExternalUserName(),
                "SPARKLE-" + licenseId,
                licenseAccount.getProvider().toLowerCase(java.util.Locale.ROOT),
                sourceOrderNo,
                user.getEmail(),
                planId == null ? "DONATION" : planId,
                product == null
                        ? consumedAmount
                        : amount(product.getAmountBase()),
                now.toInstant().toEpochMilli(),
                now.toInstant().toEpochMilli(),
                expireAt.toInstant().toEpochMilli(),
                LICENSE_DESCRIPTION,
                UUID.randomUUID().toString(),
                null
        );
        String key;
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
            key = encryptByPrivateKey(
                    payloadJson.getBytes(StandardCharsets.UTF_8),
                    settingsService.licensePrivateKey()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate PBHLicense: " + exception.getMessage());
        }
        licenseMapper.insertWithPayload(new PbhLicense()
                .setLicenseId(licenseId)
                .setBtnUserId(btnUserId)
                .setProvider(licenseAccount.getProvider())
                .setExternalUserId(licenseAccount.getExternalUserId())
                .setAfdianUserId(
                        "AFDIAN".equals(licenseAccount.getProvider())
                                ? licenseAccount.getExternalUserId()
                                : null
                )
                .setSourceType(sourceType)
                .setSourceOrderNo(sourceOrderNo)
                .setPlanId(planId)
                .setConsumedAmount(consumedAmount)
                .setLicenseKey(key)
                .setLicensePayload(payloadJson)
                .setStartAt(now)
                .setExpireAt(expireAt));
        return licenseByPublicId(licenseId);
    }

    public List<com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.LicenseView>
    recentLicenses(int limit) {
        int safeLimit = Math.min(500, Math.max(1, limit));
        List<PbhLicense> licenses = licenseMapper.selectList(
                new QueryWrapper<PbhLicense>()
                        .orderByDesc("issued_at")
                        .last("LIMIT " + safeLimit)
        );
        if (licenses.isEmpty()) return List.of();
        var users = userService.listByIds(
                licenses.stream().map(PbhLicense::getBtnUserId).distinct().toList()
        ).stream().collect(java.util.stream.Collectors.toMap(User::getId, value -> value));
        return licenses.stream().map(license -> {
            User user = users.get(license.getBtnUserId());
            return new com.ghostchu.btn.sparkle.controller.admin.dto.AfdianModels.LicenseView(
                    license.getLicenseId().toString(),
                    license.getBtnUserId(),
                    user == null ? null : user.getNickname(),
                    license.getAfdianUserId(),
                    license.getSourceType(),
                    license.getSourceOrderNo(),
                    license.getPlanId(),
                    license.getConsumedAmount().toPlainString(),
                    license.getIssuedAt(),
                    license.getExpireAt(),
                    license.getRevokedAt(),
                    license.getRevokedReason()
            );
        }).toList();
    }

    private Availability calculateAvailability(long btnUserId) {
        BigDecimal totalDonation =
                transactionMapper.sumCompletedDonationBase(btnUserId);
        int productSlots =
                transactionMapper.countAvailableProductSlots(btnUserId);
        BigDecimal price = settingsService.donationLicenseAmount();
        long donationEligible = totalDonation == null || price.signum() <= 0
                ? 0
                : totalDonation.divide(price, 0, RoundingMode.FLOOR).longValue();
        int donationUsed = licenseMapper.countDonationLicenses(btnUserId);
        long donationSlots = Math.max(
                0,
                donationEligible - donationUsed
        );
        int safeDonationSlots = donationSlots > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) donationSlots;
        return new Availability(
                productSlots,
                safeDonationSlots,
                productSlots + safeDonationSlots
        );
    }

    private List<MemberPlatformBinding> bindings(long btnUserId) {
        return bindingMapper.selectList(
                new QueryWrapper<MemberPlatformBinding>()
                        .eq("btn_user_id", btnUserId)
        );
    }

    private List<LicenseView> licenses(long btnUserId) {
        return licenseMapper.selectList(
                new QueryWrapper<PbhLicense>()
                        .eq("btn_user_id", btnUserId)
                        .orderByDesc("issued_at")
        ).stream().map(this::mapPublicLicense).toList();
    }

    private LicenseView licenseByPublicId(UUID licenseId) {
        PbhLicense license = licenseMapper.selectOne(
                new QueryWrapper<PbhLicense>().eq("license_id", licenseId)
        );
        if (license == null) {
            throw new IllegalStateException("Generated PBHLicense was not found");
        }
        return mapPublicLicense(license);
    }

    private LicenseView mapPublicLicense(PbhLicense license) {
        return new LicenseView(
                license.getLicenseId().toString(),
                license.getSourceType(),
                license.getSourceOrderNo(),
                license.getPlanId(),
                license.getConsumedAmount().toPlainString(),
                license.getLicenseKey(),
                license.getIssuedAt(),
                license.getStartAt(),
                license.getExpireAt(),
                license.getRevokedAt(),
                license.getRevokedReason()
        );
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String encryptByPrivateKey(byte[] data, String pemOrBase64)
            throws BusinessException {
        try {
            String base64 = pemOrBase64
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(encoded)
            );
            if (privateKey.getModulus().bitLength() != 1024) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "PBHLicense requires a 1024-bit RSA private key");
            }
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int offset = 0; offset < data.length; offset += RSA_BLOCK_SIZE) {
                output.write(cipher.doFinal(
                        data,
                        offset,
                        Math.min(RSA_BLOCK_SIZE, data.length - offset)
                ));
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Invalid PBHLicense RSA private key: " + exception.getMessage());
        }
    }

    private record Availability(
            int productSlots,
            int donationSlots,
            int totalSlots
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record V2LicensePayload(
            String verifyMagic,
            int licenseVersion,
            String type,
            String source,
            String licenseTo,
            String orderId,
            String paymentGateway,
            String paymentOrderId,
            String email,
            String sku,
            BigDecimal paid,
            long createAt,
            long startAt,
            long expireAt,
            String description,
            String hidden,
            List<String> features
    ) {
    }
}
