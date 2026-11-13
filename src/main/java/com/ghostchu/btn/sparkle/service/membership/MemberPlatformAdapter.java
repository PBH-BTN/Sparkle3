package com.ghostchu.btn.sparkle.service.membership;

import com.ghostchu.btn.sparkle.constants.MemberPlatform;

import java.util.List;

/**
 * A first-class platform integration backed by the provider's official API
 * (or official webhook when the provider offers no history API).
 */
public interface MemberPlatformAdapter {
    MemberPlatform provider();

    /** Official developer documentation shown to administrators. */
    String documentationUrl();

    /** Whether the provider exposes a paginated history endpoint. */
    boolean supportsHistorySync();

    /** Whether the provider sends signed payment webhooks. */
    boolean supportsWebhook();

    /** Configuration keys the administrator must provide, in display order. */
    List<String> configFields();

    /** Configuration keys that must be present before synchronization starts. */
    default List<String> requiredConfigFields() {
        return configFields();
    }

    /** Default settlement currency when the provider payload has no currency. */
    String defaultCurrency();

    /** Fetch the full transaction history. Only for history-enabled providers. */
    MemberPlatformSnapshot fetchHistory(MemberPlatformCredentials credentials)
            throws com.ghostchu.btn.sparkle.exception.BusinessException;

    /**
     * Resolve a user-supplied proof (order number, receipt ID, account email)
     * directly against the provider API. Providers without a stable buyer
     * identity use the returned transaction's external user ID as the binding
     * key.
     */
    default PlatformTransaction resolveProof(
            MemberPlatformCredentials credentials,
            String proof
    ) throws com.ghostchu.btn.sparkle.exception.BusinessException {
        return null;
    }

    /**
     * Verify and normalize one webhook delivery.
     *
     * @param rawBody   raw request body, required for signature verification
     * @param payload   parsed JSON payload
     * @param headers   provider-specific headers such as the webhook signature
     */
    default List<PlatformTransaction> parseWebhook(
            MemberPlatformCredentials credentials,
            String rawBody,
            com.fasterxml.jackson.databind.JsonNode payload,
            java.util.Map<String, String> headers
    ) throws com.ghostchu.btn.sparkle.exception.BusinessException {
        throw new com.ghostchu.btn.sparkle.exception.BusinessException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                provider().name() + " does not send webhooks"
        );
    }
}
