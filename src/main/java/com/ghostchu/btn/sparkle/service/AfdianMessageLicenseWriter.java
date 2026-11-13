package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.PendingLicense;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.MatchRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.mapper.AfdianMessageLicenseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfdianMessageLicenseWriter {
    private final AfdianMessageLicenseMapper mapper;
    private final ExchangeRateService exchangeRates;

    public List<PendingLicense> pending() {
        return mapper.pending();
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolve(MatchRequest request,
                        Long operatorId) throws BusinessException {
        if (request == null || request.licenseId() == null || request.orderId() == null
                || !request.orderId().matches("[A-Za-z0-9_-]{1,128}")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "Select a license and enter a valid order number.");
        }
        mapper.lock();
        PbhLicense license = mapper.pendingById(request.licenseId());
        if (license == null) throw new BusinessException(
                HttpStatus.CONFLICT, "This license is no longer awaiting a match. Refresh and retry.");
        MemberTransaction order = mapper.exact(license.getExternalUserId(), request.orderId());
        if (order == null) throw new BusinessException(
                HttpStatus.BAD_REQUEST, "No completed order for this Afdian account was found. Sync transactions and check the order number.");
        mapper.match(license.getId(), order.getTransactionType(), order.getExternalTransactionId(), order.getSku(), "MANUAL");
        log.info("Afdian historical license {} manually matched by administrator {}", license.getLicenseId(), operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public String register(String userId, String messageId, PbhLicenseDecoder.Decoded decoded,
                           Long operatorId) throws Exception {
        return register(userId, messageId, decoded, operatorId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public String register(String userId, String messageId, PbhLicenseDecoder.Decoded decoded,
                           Long operatorId, AfdianMessageReceipt receipt) throws Exception {
        mapper.lock();
        PbhLicense existing = mapper.existing(decoded.key());
        if (existing != null && (!"AFDIAN".equals(existing.getProvider())
                || !userId.equals(existing.getExternalUserId()))) return "CONFLICT";
        if (existing != null) {
            String status = mapper.matchStatus(existing.getId());
            if (!"UNMATCHED".equals(status) && !(receipt != null && "HEURISTIC".equals(status))) return "DUPLICATE";
        }
        String orderId = decoded.payload().path("paymentOrderId").asText("");
        MemberTransaction order = orderId.isBlank() ? null : mapper.exact(userId, orderId);
        String match = "EXACT";
        if (receipt != null && (orderId.isBlank() || orderId.equals(receipt.orderId()))) {
            // The card identifies the order even when it has not reached the local ledger yet.
            MemberTransaction local = mapper.exact(userId, receipt.orderId());
            order = local == null ? new MemberTransaction().setExternalTransactionId(receipt.orderId())
                    .setTransactionType(receipt.sourceType()).setSku(receipt.sku())
                    .setAmountBase(exchangeRates.convert(receipt.amountCny(), "CNY")) : local;
            match = "MESSAGE_CARD";
        } else if (orderId.isBlank()) {
            var candidates = mapper.candidates(userId, decoded.createdAt());
            if (candidates.size() == 1) order = candidates.getFirst();
            match = "HEURISTIC";
        }
        if (existing != null) {
            if (order == null) return "UNMATCHED";
            mapper.matchFromMessage(existing.getId(), order.getTransactionType(), order.getExternalTransactionId(),
                    order.getSku(), order.getAmountBase() == null ? BigDecimal.ZERO : order.getAmountBase(), match);
            log.info("Afdian message license reconciled by administrator {}", operatorId);
            return "RECONCILED";
        }
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(decoded.key().getBytes(StandardCharsets.US_ASCII)));
        PbhLicense license = new PbhLicense().setLicenseId(UUID.nameUUIDFromBytes(
                        decoded.key().getBytes(StandardCharsets.US_ASCII)))
                .setProvider("AFDIAN").setExternalUserId(userId)
                .setSourceType(order == null ? "DONATION" : order.getTransactionType())
                .setSourceOrderNo(order == null ? (orderId.isBlank() ? null : orderId) : order.getExternalTransactionId())
                .setPlanId(order == null ? null : order.getSku())
                .setConsumedAmount(order == null || order.getAmountBase() == null ? BigDecimal.ZERO : order.getAmountBase())
                .setLicenseKey(decoded.key()).setLicensePayload(decoded.payload().toString())
                .setIssuedAt(decoded.createdAt()).setStartAt(decoded.startAt()).setExpireAt(decoded.expireAt());
        int inserted = mapper.insert(license, fingerprint, messageId, order == null ? "UNMATCHED" : match, operatorId);
        if (inserted == 0) return "DUPLICATE";
        log.info("Afdian message license imported by administrator {}, match {}", operatorId, order == null ? "UNMATCHED" : match);
        return order == null ? "UNMATCHED" : "IMPORTED";
    }
}
