package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseMapper;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseRevocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
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
    public void revoke(UUID licenseId, String key, String reason, long actor) throws BusinessException {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Provide a revocation reason (up to 500 characters)");
        }
        if ((licenseId == null) == (key == null || key.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Select one license or provide one license key");
        }
        JsonNode payload;
        if (licenseId != null) {
            PbhLicense license = licenses.selectOne(new QueryWrapper<PbhLicense>().eq("license_id", licenseId));
            if (license == null) throw new BusinessException(HttpStatus.NOT_FOUND, "License not found");
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
