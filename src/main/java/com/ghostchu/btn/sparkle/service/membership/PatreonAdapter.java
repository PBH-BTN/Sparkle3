package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Patreon API v2 adapter.
 *
 * <p>Patreon only sells memberships through its public API: there is no shop
 * or product resource in API v2. Every patron is therefore a donation, and the
 * cumulative paid amount comes from the member's campaign lifetime support
 * ({@code campaign_lifetime_support_cents}).</p>
 */
@Component
public class PatreonAdapter implements MemberPlatformAdapter {
    static final String API_BASE = "https://www.patreon.com/api/oauth2/v2";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final String DOCUMENTATION_URL = "https://docs.patreon.com/";

    private static final String MEMBER_FIELDS = String.join(",",
            "patron_status",
            "campaign_lifetime_support_cents",
            "currently_entitled_amount_cents",
            "pledge_relationship_start",
            "last_charge_date",
            "last_charge_status",
            "email"
    );

    private final MemberPlatformHttpClient httpClient;

    public PatreonAdapter(MemberPlatformHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MemberPlatform provider() {
        return MemberPlatform.PATREON;
    }

    @Override
    public String documentationUrl() {
        return DOCUMENTATION_URL;
    }

    @Override
    public boolean supportsHistorySync() {
        return true;
    }

    @Override
    public boolean supportsWebhook() {
        return true;
    }

    @Override
    public List<String> configFields() {
        return List.of("accessToken", "accountId", "webhookToken");
    }

    @Override
    public List<String> requiredConfigFields() {
        return List.of("accessToken");
    }

    @Override
    public String defaultCurrency() {
        return "USD";
    }

    @Override
    public MemberPlatformSnapshot fetchHistory(MemberPlatformCredentials credentials)
            throws BusinessException {
        if (!credentials.hasAccessToken()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Patreon creator access token is not configured");
        }
        CampaignContext campaign = resolveCampaign(credentials);
        String cursor = null;
        List<PlatformTransaction> transactions = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> params = new HashMap<>();
            params.put("include", "currently_entitled_tiers,user");
            params.put("fields[member]", MEMBER_FIELDS);
            params.put("fields[user]", "full_name,email,image_url");
            params.put("fields[tier]", "title,amount_cents");
            params.put("page[count]", Integer.toString(PAGE_SIZE));
            if (cursor != null && !cursor.isBlank()) {
                params.put("page[cursor]", cursor);
            }
            JsonNode root = httpClient.get(
                    query(API_BASE + "/campaigns/" + campaign.campaignId() + "/members", params),
                    credentials.accessToken()
            );
            List<PlatformTransaction> pageTransactions = mapMembers(
                    root,
                    campaign.currency()
            );
            transactions.addAll(pageTransactions);
            cursor = text(root.path("meta").path("pagination")
                    .path("cursors").path("next"));
            if (cursor == null || cursor.isBlank()) {
                return new MemberPlatformSnapshot(transactions, campaign.campaignId());
            }
        }
        throw new BusinessException(HttpStatus.BAD_GATEWAY,
                "Patreon pagination exceeded the " + MAX_PAGES + " page safety limit");
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
                    "Patreon webhook secret is not configured");
        }
        verifySignature(
                rawBody,
                credentials.webhookToken(),
                headers.get("x-patreon-signature")
        );
        if (!"member".equals(text(payload.path("data").path("type")))) {
            // The endpoint only subscribes to member triggers; ignore others.
            return List.of();
        }
        return mapMembers(payload, campaignCurrency(payload, credentials.currency()));
    }

    private CampaignContext resolveCampaign(MemberPlatformCredentials credentials)
            throws BusinessException {
        String configured = credentials.accountId();
        if (configured != null && !configured.isBlank()) {
            return new CampaignContext(configured.trim(), credentials.currency());
        }
        Map<String, String> params = new HashMap<>();
        params.put("include", "campaign");
        params.put("fields[campaign]", "currency");
        JsonNode root = httpClient.get(
                query(API_BASE + "/identity", params),
                credentials.accessToken()
        );
        JsonNode campaign = root.path("data").path("relationships")
                .path("campaign").path("data");
        String id = text(campaign.path("id"));
        if (id == null || id.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Patreon did not return a campaign for this creator token");
        }
        String currency = findCampaignCurrency(root);
        return new CampaignContext(id, currency == null
                ? credentials.currency()
                : currency);
    }

    private List<PlatformTransaction> mapMembers(JsonNode root, String currency) {
        Map<String, JsonNode> included = includedByTypeAndId(root);
        List<PlatformTransaction> transactions = new ArrayList<>();
        for (JsonNode member : dataNodes(root)) {
            PlatformTransaction transaction = mapMember(member, included, currency);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }
        return transactions;
    }

    private List<JsonNode> dataNodes(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isArray()) {
            return array(data);
        }
        return data.isObject() ? List.of(data) : List.of();
    }

    private PlatformTransaction mapMember(
            JsonNode member,
            Map<String, JsonNode> included,
            String currency
    ) {
        String memberId = text(member.path("id"));
        if (memberId == null || memberId.isBlank()) {
            return null;
        }
        JsonNode attributes = member.path("attributes");
        long lifetimeCents = attributes.path("campaign_lifetime_support_cents")
                .asLong(attributes.path("lifetime_support_cents").asLong(0));
        long entitledCents = attributes.path("currently_entitled_amount_cents")
                .asLong(0);
        long paidCents = Math.max(lifetimeCents, entitledCents);
        if (paidCents <= 0) {
            return null;
        }

        String userId = text(member.path("relationships").path("user")
                .path("data").path("id"));
        JsonNode user = userId == null
                ? null
                : included.get("user:" + userId);
        String email = firstNonBlank(
                text(attributes.path("email")),
                user == null ? null : text(user.path("attributes").path("email"))
        );
        String name = user == null
                ? null
                : text(user.path("attributes").path("full_name"));
        String avatar = user == null
                ? null
                : text(user.path("attributes").path("image_url"));

        Set<String> tierIds = new LinkedHashSet<>();
        List<String> tierTitles = new ArrayList<>();
        for (JsonNode tier : array(member.path("relationships")
                .path("currently_entitled_tiers").path("data"))) {
            String tierId = text(tier.path("id"));
            if (tierId == null || tierId.isBlank()) {
                continue;
            }
            tierIds.add(tierId);
            JsonNode includedTier = included.get("tier:" + tierId);
            String title = includedTier == null
                    ? null
                    : text(includedTier.path("attributes").path("title"));
            if (title != null && !title.isBlank()) {
                tierTitles.add(title);
            }
        }

        String externalUserId = email != null && !email.isBlank()
                ? email.trim().toLowerCase(Locale.ROOT)
                : "patreon:" + (userId == null || userId.isBlank() ? memberId : userId);

        return new PlatformTransaction(
                "member:" + memberId,
                externalUserId,
                blankToNull(name),
                blankToNull(avatar),
                PlatformTransaction.DONATION,
                tierIds.isEmpty() ? null : String.join(",", tierIds),
                tierTitles.isEmpty() ? null : String.join(" / ", tierTitles),
                BigDecimal.valueOf(paidCents, 2),
                currency,
                memberStatus(attributes, lifetimeCents, entitledCents),
                time(attributes),
                member
        );
    }

    private String memberStatus(
            JsonNode attributes,
            long lifetimeCents,
            long entitledCents
    ) {
        String patronStatus = text(attributes.path("patron_status"));
        if ("active_patron".equalsIgnoreCase(patronStatus)) {
            return "ACTIVE";
        }
        if (lifetimeCents > 0) {
            // Former patrons still paid historically; keep the payment counted
            // while the raw patron status stays available in the audit payload.
            return "COMPLETED";
        }
        return entitledCents > 0 ? "COMPLETED" : "FORMER";
    }

    private OffsetDateTime time(JsonNode attributes) {
        OffsetDateTime value = parseTime(text(attributes.path("pledge_relationship_start")));
        if (value != null) {
            return value;
        }
        return parseTime(text(attributes.path("last_charge_date")));
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            // Fall through to the date-only parser.
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // Fall through to the date-only parser.
        }
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String campaignCurrency(JsonNode payload, String fallback) {
        String currency = findCampaignCurrency(payload);
        return currency == null || currency.isBlank() ? fallback : currency;
    }

    private String findCampaignCurrency(JsonNode root) {
        for (JsonNode node : array(root.path("included"))) {
            if ("campaign".equals(text(node.path("type")))) {
                String currency = text(node.path("attributes").path("currency"));
                if (currency != null && !currency.isBlank()) {
                    return currency.toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private Map<String, JsonNode> includedByTypeAndId(JsonNode root) {
        Map<String, JsonNode> values = new HashMap<>();
        for (JsonNode node : array(root.path("included"))) {
            String type = text(node.path("type"));
            String id = text(node.path("id"));
            if (type != null && id != null) {
                values.put(type + ":" + id, node);
            }
        }
        return values;
    }

    private void verifySignature(
            String rawBody,
            String secret,
            String signature
    ) throws BusinessException {
        if (rawBody == null || signature == null || signature.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Patreon webhook signature is missing");
        }
        String expected;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacMD5");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacMD5"
            ));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            expected = hex.toString();
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to verify the Patreon webhook signature");
        }
        if (!java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "Patreon webhook signature is invalid");
        }
    }

    private String query(String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(path);
        char separator = '?';
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append(separator)
                    .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            separator = '&';
        }
        return url.toString();
    }

    private List<JsonNode> array(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(values::add);
        }
        return values;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CampaignContext(String campaignId, String currency) {
    }
}
