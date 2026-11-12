package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AfdianSettingsService {
    public static final String API_BASE_URL_KEY = "sparkle.afdian.api-base-url";
    public static final String USER_ID_KEY = "sparkle.afdian.user-id";
    public static final String ACCESS_TOKEN_KEY = "sparkle.afdian.access-token";
    public static final String SYNC_ENABLED_KEY = "sparkle.afdian.sync-enabled";
    public static final String LICENSE_PLAN_IDS_KEY = "sparkle.afdian.license-plan-ids";
    public static final String LICENSE_DONATION_AMOUNT_KEY =
            "sparkle.afdian.license-donation-amount";
    public static final String LICENSE_VALID_YEARS_KEY =
            "sparkle.afdian.license-valid-years";
    public static final String LICENSE_PRIVATE_KEY_KEY =
            "sparkle.afdian.license-private-key";

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;

    public AfdianSettingsService(
            DatabaseConfigService databaseConfigService,
            Environment environment
    ) {
        this.databaseConfigService = databaseConfigService;
        this.environment = environment;
    }

    public String apiBaseUrl() {
        return normalizeBaseUrl(raw(API_BASE_URL_KEY, "https://afdian.com/api/open"));
    }

    public String userId() {
        return raw(USER_ID_KEY, "").trim();
    }

    public String accessToken() {
        return raw(ACCESS_TOKEN_KEY, "").trim();
    }

    public boolean tokenConfigured() {
        return !accessToken().isBlank();
    }

    public boolean configured() {
        return !userId().isBlank() && tokenConfigured();
    }

    public boolean syncEnabled() {
        return Boolean.parseBoolean(raw(SYNC_ENABLED_KEY, "true"));
    }

    public String licensePlanIdsRaw() {
        return raw(LICENSE_PLAN_IDS_KEY, "");
    }

    public Set<String> licensePlanIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String value : licensePlanIdsRaw().split("[,;\\s]+")) {
            if (!value.isBlank()) {
                ids.add(value.trim());
            }
        }
        return Set.copyOf(ids);
    }

    public BigDecimal donationLicenseAmount() {
        try {
            return new BigDecimal(raw(LICENSE_DONATION_AMOUNT_KEY, "50").trim());
        } catch (NumberFormatException exception) {
            return new BigDecimal("50");
        }
    }

    public int licenseValidYears() {
        try {
            return Integer.parseInt(raw(LICENSE_VALID_YEARS_KEY, "100").trim());
        } catch (NumberFormatException exception) {
            return 100;
        }
    }

    public String licensePrivateKey() {
        return raw(LICENSE_PRIVATE_KEY_KEY, "");
    }

    public boolean licensePrivateKeyConfigured() {
        return !licensePrivateKey().isBlank();
    }

    public void update(
            String userId,
            @Nullable String accessToken,
            boolean clearToken,
            String apiBaseUrl,
            boolean syncEnabled,
            String licensePlanIds,
            String licenseDonationAmount,
            int licenseValidYears,
            @Nullable String licensePrivateKey,
            boolean clearLicensePrivateKey,
            @Nullable Long operatorId
    ) throws BusinessException {
        String normalizedUserId = userId == null ? "" : userId.trim();
        if (normalizedUserId.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian user ID is too long");
        }
        String normalizedBaseUrl = validateApiBaseUrl(apiBaseUrl);
        String normalizedPlanIds = normalizePlanIds(licensePlanIds);
        BigDecimal donationAmount;
        try {
            donationAmount = new BigDecimal(
                    licenseDonationAmount == null ? "" : licenseDonationAmount.trim()
            );
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "License donation amount must be a valid number");
        }
        if (donationAmount.signum() <= 0
                || donationAmount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "License donation amount must be between 0 and 1,000,000");
        }
        if (licenseValidYears < 1 || licenseValidYears > 1_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "License validity must be between 1 and 1,000 years");
        }
        databaseConfigService.update(USER_ID_KEY, normalizedUserId, operatorId);
        databaseConfigService.update(API_BASE_URL_KEY, normalizedBaseUrl, operatorId);
        databaseConfigService.update(
                SYNC_ENABLED_KEY,
                Boolean.toString(syncEnabled),
                operatorId
        );
        databaseConfigService.update(
                LICENSE_PLAN_IDS_KEY,
                normalizedPlanIds,
                operatorId
        );
        databaseConfigService.update(
                LICENSE_DONATION_AMOUNT_KEY,
                donationAmount.stripTrailingZeros().toPlainString(),
                operatorId
        );
        databaseConfigService.update(
                LICENSE_VALID_YEARS_KEY,
                String.valueOf(licenseValidYears),
                operatorId
        );
        if (clearToken) {
            databaseConfigService.updateSensitive(ACCESS_TOKEN_KEY, "", operatorId);
        } else if (accessToken != null && !accessToken.isBlank()) {
            String normalizedToken = accessToken.trim();
            if (normalizedToken.length() > 4_096) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Afdian access token is too long");
            }
            databaseConfigService.updateSensitive(
                    ACCESS_TOKEN_KEY,
                    normalizedToken,
                    operatorId
            );
        }
        if (clearLicensePrivateKey) {
            databaseConfigService.updateSensitive(
                    LICENSE_PRIVATE_KEY_KEY,
                    "",
                    operatorId
            );
        } else if (licensePrivateKey != null && !licensePrivateKey.isBlank()) {
            if (licensePrivateKey.length() > 16_384) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "License private key is too long");
            }
            databaseConfigService.updateSensitive(
                    LICENSE_PRIVATE_KEY_KEY,
                    licensePrivateKey.trim(),
                    operatorId
            );
        }
    }

    public String validateApiBaseUrl(String value) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian API base URL is required");
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian API base URL is invalid");
        }
        if (uri.getScheme() == null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian API base URL must be an HTTP(S) URL without query or fragment");
        }
        return normalizeBaseUrl(normalized);
    }

    private String normalizeBaseUrl(String value) {
        return value.trim().replaceAll("/+$", "");
    }

    private String normalizePlanIds(String value) {
        if (value == null) return "";
        return String.join(",", licensePlanIdsFrom(value));
    }

    private Set<String> licensePlanIdsFrom(String value) {
        Set<String> ids = new LinkedHashSet<>();
        for (String item : value.split("[,;\\s]+")) {
            if (!item.isBlank()) {
                ids.add(item.trim());
            }
        }
        return ids;
    }

    private String raw(String key, String fallback) {
        Map<String, String> values = databaseConfigService.getRawValues(List.of(key));
        String value = values.get(key);
        if (value != null) {
            return value;
        }
        return environment.getProperty(key, fallback);
    }
}
