package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseMapper;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseRevocationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PbhLicenseRevocationService {
    static final List<String> FIELDS = List.of("licenseTo", "description", "orderId", "paymentOrderId",
            "paymentGateway", "sku", "source", "type");
    private final PbhLicenseRevocationMapper mapper;
    private final PbhLicenseMapper licenses;
    private final PbhLicenseDecoder decoder;
    private final AfdianSettingsService settings;
    private final ObjectMapper json = new ObjectMapper();

    public boolean check(Map<String, String> params) {
        Map<String, String> hashes = new TreeMap<>();
        for (String field : FIELDS) {
            String value = params.get(field);
            if (value != null) {
                if (!value.matches("[0-9a-f]{64}")) return false;
                hashes.put(field, value);
            }
        }
        return hashes.containsKey("licenseTo") && mapper.isRevoked(fingerprint(hashes));
    }

    public Map<String, Object> list(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return Map.of("items", mapper.list(safeSize, (long) (safePage - 1) * safeSize),
                "total", mapper.count(), "page", safePage, "size", safeSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(UUID licenseId, String key, String reason, long actor,
                       boolean returnSlot, boolean delete) throws BusinessException {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Provide a revocation reason (up to 500 characters)");
        }
        if ((licenseId == null) == (key == null || key.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Select one license or provide one license key");
        }
        if (licenseId == null && (returnSlot || delete)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Select an issued license for this operation");
        }
        // Use the same lock as issuance and historical imports before changing entitlement accounting.
        licenses.lockKey("sparkle:pbh-import");
        JsonNode payload;
        PbhLicense license = null;
        if (licenseId != null) {
            license = licenses.selectOne(new QueryWrapper<PbhLicense>().eq("license_id", licenseId)
                    .isNull("deleted_at").last("FOR UPDATE"));
            if (license == null) throw new BusinessException(HttpStatus.NOT_FOUND, "License not found");
            if (delete && !returnSlot && !license.isSlotReturned() && "UNMATCHED".equals(license.getImportMatch())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Match this historical license to an order or return its slot before deleting it");
            }
            key = license.getLicenseKey();
            try {
                payload = json.readTree(license.getLicensePayload());
                if (payload == null || !payload.hasNonNull("licenseTo")) throw new IllegalArgumentException();
            } catch (Exception e) {
                throw new BusinessException(HttpStatus.CONFLICT, "Unable to read this license");
            }
        } else {
            if (key.length() > 40000) throw new BusinessException(HttpStatus.BAD_REQUEST, "License key is too long");
            try {
                var decoded = decoder.scan(key, decoder.verificationKeys(settings.licensePrivateKey()));
                if (decoded.size() != 1) throw new IllegalArgumentException();
                payload = decoded.getFirst().payload();
                key = decoded.getFirst().key();
            } catch (Exception e) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Provide a valid PeerBanHelper license key");
            }
        }
        Map<String, String> hashes = hashes(payload);
        String encoded;
        try {
            encoded = json.writeValueAsString(hashes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        mapper.revoke(fingerprint(hashes), encoded, reason.trim(), actor);
        mapper.markLicense(key, reason.trim());
        boolean returned = false;
        if (returnSlot && mapper.returnSlot(licenseId) > 0) {
            returned = true;
            if (license.getSourceOrderNo() != null) {
                mapper.releaseOrder(license.getProvider(), license.getExternalUserId(), license.getSourceOrderNo());
            }
        }
        if (delete) mapper.deleteLicense(licenseId);
        log.info("Administrator {} revoked license {}, deleted={}, slotReturned={}", actor,
                licenseId == null ? fingerprint(hashes) : licenseId, delete, returned);
    }

    static Map<String, String> hashes(JsonNode payload) {
        Map<String, String> hashes = new TreeMap<>();
        boolean v1 = payload.path("licenseVersion").asInt(1) == 1;
        for (String field : FIELDS) {
            // Match V1 getters and the default type used by PeerBanHelper.
            if (v1 && List.of("orderId", "paymentOrderId", "paymentGateway", "sku").contains(field)) continue;
            JsonNode node = payload.get(field);
            if (node != null && !node.isNull()) hashes.put(field, sha256(node.asText()));
            else if (v1 && field.equals("type") && node == null) hashes.put(field, sha256("afdian"));
        }
        return hashes;
    }

    static String fingerprint(Map<String, String> hashes) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(hashes).forEach((field, hash) -> canonical.append(field).append('=').append(hash).append('\n'));
        return sha256(canonical.toString());
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
