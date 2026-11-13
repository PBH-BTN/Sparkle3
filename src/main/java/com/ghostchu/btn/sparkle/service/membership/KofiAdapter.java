package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ko-fi adapter.
 *
 * <p>Ko-fi has no public read API; its only developer surface is the payment
 * webhook for donations, membership payments, shop orders, and commissions.
 * Users bind by the transaction ID from their Ko-fi receipt.</p>
 */
@Component
public class KofiAdapter implements MemberPlatformAdapter {
    private static final String DOCUMENTATION_URL =
            "https://help.ko-fi.com/hc/en-us/articles/360004162298-Does-Ko-fi-have-an-API-or-webhook";

    @Override
    public MemberPlatform provider() {
        return MemberPlatform.KOFI;
    }

    @Override
    public String documentationUrl() {
        return DOCUMENTATION_URL;
    }

    @Override
    public boolean supportsHistorySync() {
        return false;
    }

    @Override
    public boolean supportsWebhook() {
        return true;
    }

    @Override
    public List<String> configFields() {
        return List.of("webhookToken");
    }

    @Override
    public String defaultCurrency() {
        return "CNY";
    }

    @Override
    public MemberPlatformSnapshot fetchHistory(MemberPlatformCredentials credentials)
            throws BusinessException {
        throw new BusinessException(HttpStatus.CONFLICT,
                "Ko-fi does not expose a history API; configure its webhook instead");
    }

    @Override
    public List<PlatformTransaction> parseWebhook(
            MemberPlatformCredentials credentials,
            String rawBody,
            JsonNode payload,
            Map<String, String> headers
    ) throws BusinessException {
        if (!credentials.hasWebhookToken()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ko-fi verification token is not configured");
        }
        String provided = firstNonBlank(
                text(payload.path("verification_token")),
                headers.get("x-sparkle-token")
        );
        if (!constantEquals(credentials.webhookToken(), provided)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Ko-fi webhook verification token is invalid");
        }
        String type = text(payload.path("type"));
        String transactionId = firstNonBlank(
                text(payload.path("kofi_transaction_id")),
                text(payload.path("message_id"))
        );
        if (transactionId == null || transactionId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Ko-fi webhook payload is missing its transaction ID");
        }
        String email = firstNonBlank(
                text(payload.path("email")),
                firstNonBlank(text(payload.path("from_email")),
                        text(payload.path("fromEmail")))
        );
        String name = text(payload.path("from_name"));
        String currency = firstNonBlank(text(payload.path("currency")),
                credentials.currency() == null ? defaultCurrency() : credentials.currency());
        OffsetDateTime occurredAt = time(payload.path("timestamp"));
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        BigDecimal amount = decimal(payload.path("amount"));
        String externalUserId = externalUserId(email, name, transactionId);

        if (normalizedType.contains("shop")) {
            // Ko-fi sends one order with item codes but no line prices. Keep it
            // as a single product transaction so one order grants one slot.
            return List.of(new PlatformTransaction(
                    transactionId,
                    externalUserId,
                    blankToNull(name),
                    null,
                    PlatformTransaction.PRODUCT,
                    limited(joinShopField(payload, "direct_link_code"), 255),
                    limited(joinShopField(payload, "variation_name"), 255),
                    amount,
                    currency,
                    "COMPLETED",
                    occurredAt,
                    payload
            ));
        }
        boolean subscription = normalizedType.contains("subscription")
                || payload.path("is_subscription_payment").asBoolean(false);
        String tierName = text(payload.path("tier_name"));
        return List.of(new PlatformTransaction(
                transactionId,
                externalUserId,
                blankToNull(name),
                null,
                PlatformTransaction.DONATION,
                subscription ? blankToNull(tierName) : null,
                subscription
                        ? blankToNull(tierName)
                        : (normalizedType.contains("commission")
                        ? "Ko-fi commission"
                        : null),
                amount,
                currency,
                "COMPLETED",
                occurredAt,
                payload
        ));
    }

    private String joinShopField(JsonNode payload, String field) {
        JsonNode items = payload.path("shop_items");
        if (!items.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : items) {
            String value = text(item.path(field));
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String limited(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String externalUserId(String email, String name, String transactionId) {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        if (name != null && !name.isBlank()) {
            return "kofi:" + name.trim().toLowerCase(Locale.ROOT);
        }
        return "kofi:" + transactionId;
    }

    private OffsetDateTime time(JsonNode node) {
        String value = text(node);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String value = node.asText("");
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private boolean constantEquals(String expected, String provided) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided == null
                        ? new byte[0]
                        : provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
