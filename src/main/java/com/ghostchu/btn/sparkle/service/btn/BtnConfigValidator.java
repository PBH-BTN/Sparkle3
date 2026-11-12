package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;

import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BtnConfigValidator {
    private BtnConfigValidator() {
    }

    public static Map<String, String> validate(
            Map<String, String> requestedValues,
            Map<String, String> currentValues
    ) throws BusinessException {
        if (requestedValues == null || requestedValues.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "At least one BTN configuration value is required"
            );
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : requestedValues.entrySet()) {
            BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(entry.getKey());
            if (definition == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown BTN configuration key: " + entry.getKey()
                );
            }
            normalized.put(definition.key(), normalize(definition, entry.getValue()));
        }

        Map<String, String> merged = new LinkedHashMap<>(currentValues);
        merged.putAll(normalized);
        validateCrossFieldRules(merged);
        return normalized;
    }

    private static String normalize(
            BtnConfigCatalog.Definition definition,
            String rawValue
    ) throws BusinessException {
        if (rawValue == null) {
            throw invalid(definition, "value is required");
        }
        String value = rawValue.trim();
        if (definition.type() == BtnConfigCatalog.ValueType.SECRET) {
            if (value.length() > 4_096) {
                throw invalid(definition, "value is too long");
            }
            return value;
        }
        if (value.isEmpty()) {
            throw invalid(definition, "value must not be empty");
        }
        return switch (definition.type()) {
            case BOOLEAN -> normalizeBoolean(definition, value);
            case INTEGER -> normalizeInteger(definition, value);
            case DECIMAL -> normalizeDecimal(definition, value);
            case ENDPOINT -> normalizeEndpoint(definition, value);
            case URL -> normalizeUrl(definition, value);
            case ALGORITHM -> normalizeAlgorithm(definition, value);
            case TEXT -> normalizeText(definition, value);
            case LIST -> normalizeList(definition, value);
            case TIME_ZONE -> normalizeTimeZone(definition, value);
            case EMAIL -> normalizeEmail(definition, value);
            case SECRET -> value;
            case CRON -> normalizeCron(definition, value);
        };
    }

    private static String normalizeBoolean(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw invalid(definition, "value must be true or false");
        }
        return normalized;
    }

    private static String normalizeInteger(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(definition, "value must be an integer");
        }
        if (definition.min() != null && parsed < definition.min()) {
            throw invalid(definition, "value must be at least " + definition.min());
        }
        if (definition.max() != null && parsed > definition.max()) {
            throw invalid(definition, "value must be at most " + definition.max());
        }
        return Long.toString(parsed);
    }

    private static String normalizeDecimal(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalid(definition, "value must be numeric");
        }
        if (definition.min() != null
                && parsed.compareTo(BigDecimal.valueOf(definition.min())) < 0) {
            throw invalid(definition, "value must be at least " + definition.min());
        }
        if (definition.max() != null
                && parsed.compareTo(BigDecimal.valueOf(definition.max())) > 0) {
            throw invalid(definition, "value must be at most " + definition.max());
        }
        return parsed.stripTrailingZeros().toPlainString();
    }

    private static String normalizeEndpoint(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        if (value.length() > 2_048 || value.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(definition, "endpoint is invalid");
        }
        if (value.startsWith("{rooturl}/") || value.startsWith("/")) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Report the same validation error for malformed and unsupported endpoints.
        }
        throw invalid(
                definition,
                "endpoint must be an absolute HTTP(S) URL, a path, or start with {rooturl}/"
        );
    }

    private static String normalizeCron(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        if ("-".equals(value)) {
            return value;
        }
        try {
            CronExpression.parse(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw invalid(definition, "invalid Spring cron expression");
        }
    }

    private static String normalizeUrl(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getQuery() == null
                    && uri.getFragment() == null) {
                return value.endsWith("/")
                        ? value.substring(0, value.length() - 1)
                        : value;
            }
        } catch (IllegalArgumentException ignored) {
            // Report the same domain error for every malformed value.
        }
        throw invalid(definition, "value must be an absolute HTTP(S) URL without a query or fragment");
    }

    private static String normalizeAlgorithm(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        if (value.length() > 64 || value.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(definition, "algorithm name is invalid");
        }
        try {
            MessageDigest.getInstance(value);
            return value;
        } catch (NoSuchAlgorithmException exception) {
            throw invalid(definition, "algorithm is not supported by this Java runtime");
        }
    }

    private static String normalizeText(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        if (value.length() > 16_384) {
            throw invalid(definition, "value is too long");
        }
        return value;
    }

    private static String normalizeList(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        if (value.length() > 16_384) {
            throw invalid(definition, "value is too long");
        }
        String[] entries = value.replace('\n', ',').split(",");
        String normalized = Arrays.stream(entries)
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (normalized.isEmpty()) {
            throw invalid(definition, "value must not be empty");
        }
        return normalized;
    }

    private static String normalizeTimeZone(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException exception) {
            throw invalid(definition, "time zone is invalid");
        }
    }

    private static String normalizeEmail(
            BtnConfigCatalog.Definition definition,
            String value
    ) throws BusinessException {
        if (value.length() > 320
                || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw invalid(definition, "email address is invalid");
        }
        return value;
    }

    private static void validateCrossFieldRules(Map<String, String> values)
            throws BusinessException {
        long minProtocol = longValue(values, "protocol", "minProtocolVersion");
        long maxProtocol = longValue(values, "protocol", "maxProtocolVersion");
        if (minProtocol > maxProtocol) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum BTN protocol version must not exceed the maximum version"
            );
        }

        long batchSize = longValue(values, "heartbeatQueue", "batchSize");
        long maxQueueSize = longValue(values, "heartbeatQueue", "maxSize");
        if (batchSize > maxQueueSize) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Heartbeat queue batch size must not exceed the maximum queue size"
            );
        }
    }

    private static long longValue(
            Map<String, String> values,
            String section,
            String field
    ) throws BusinessException {
        String key = BtnConfigCatalog.key(section, field);
        try {
            return Long.parseLong(values.get(key));
        } catch (Exception exception) {
            BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(key);
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid BTN configuration value for " + definition.field()
            );
        }
    }

    private static BusinessException invalid(
            BtnConfigCatalog.Definition definition,
            String reason
    ) {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "Invalid " + definition.key() + ": " + reason
        );
    }
}
