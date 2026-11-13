package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 面包多 (Mianbaoduo) creator API adapter.
 *
 * <p>The official API is documented at https://mbd.pub/open_doc/ and uses a
 * developer key plus the {@code x-token} header. Sales are one-time product
 * orders; the API exposes no buyer identifier, so each order becomes its own
 * binding identity and users claim an order by its order number.</p>
 */
@Component
public class MianbaoduoAdapter implements MemberPlatformAdapter {
    static final String API_BASE = "https://x.mbd.pub/api";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final String DOCUMENTATION_URL = "https://mbd.pub/open_doc/";

    private final MemberPlatformHttpClient httpClient;

    public MianbaoduoAdapter(MemberPlatformHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MemberPlatform provider() {
        return MemberPlatform.MIANBAODUO;
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
        return false;
    }

    @Override
    public List<String> configFields() {
        return List.of("accessToken");
    }

    @Override
    public String defaultCurrency() {
        return "CNY";
    }

    @Override
    public MemberPlatformSnapshot fetchHistory(MemberPlatformCredentials credentials)
            throws BusinessException {
        if (!credentials.hasAccessToken()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mianbaoduo developer key is not configured");
        }
        Map<String, String> productNames = productNames(credentials);
        List<PlatformTransaction> transactions = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        int seen = 0;
        for (int page = 1; page <= MAX_PAGES && seen < total; page++) {
            JsonNode result = request(
                    "/order-list?page=" + page + "&limit=" + PAGE_SIZE,
                    credentials
            );
            total = result.path("count").asInt(0);
            JsonNode orders = result.path("orders");
            if (!orders.isArray() || orders.isEmpty()) {
                break;
            }
            for (JsonNode order : orders) {
                PlatformTransaction transaction = mapOrder(order, productNames);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
            seen += orders.size();
            if (orders.size() < PAGE_SIZE) {
                break;
            }
        }
        return new MemberPlatformSnapshot(transactions, null);
    }

    @Override
    public PlatformTransaction resolveProof(
            MemberPlatformCredentials credentials,
            String proof
    ) throws BusinessException {
        if (!credentials.hasAccessToken()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mianbaoduo developer key is not configured");
        }
        JsonNode order = orderDetail(credentials, "order_id", proof);
        if (order == null) {
            order = orderDetail(credentials, "out_order_id", proof);
        }
        if (order == null) {
            return null;
        }
        String state = text(order.path("state"));
        if (state == null || !"success".equalsIgnoreCase(state)) {
            return null;
        }
        return mapOrder(order, productNames(credentials), "success");
    }

    private JsonNode orderDetail(
            MemberPlatformCredentials credentials,
            String parameter,
            String value
    ) throws BusinessException {
        try {
            return request(
                    "/order-detail?" + parameter + "="
                            + URLEncoder.encode(value, StandardCharsets.UTF_8),
                    credentials
            );
        } catch (BusinessException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    || exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                return null;
            }
            throw exception;
        }
    }

    private JsonNode request(String path, MemberPlatformCredentials credentials)
            throws BusinessException {
        JsonNode root = httpClient.get(
                API_BASE + path,
                null,
                Map.of("x-token", credentials.accessToken())
        );
        int code = root.path("code").asInt(0);
        if (code == 403) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Mianbaoduo rejected the configured developer key");
        }
        if (code == 400) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Mianbaoduo could not find that order");
        }
        if (code != 200) {
            String error = text(root.path("error_info"));
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    error == null || error.isBlank()
                            ? "Mianbaoduo API returned an unexpected response"
                            : "Mianbaoduo API error: " + error);
        }
        return root.path("result");
    }

    private Map<String, String> productNames(MemberPlatformCredentials credentials) {
        Map<String, String> names = new HashMap<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode result = request(
                        "/product-list?page=" + page + "&limit=" + PAGE_SIZE,
                        credentials
                );
                JsonNode products = result.path("products");
                if (!products.isArray() || products.isEmpty()) {
                    break;
                }
                for (JsonNode product : products) {
                    String urlkey = text(product.path("urlkey"));
                    String name = text(product.path("productname"));
                    if (urlkey != null && name != null) {
                        names.put(urlkey, name);
                    }
                }
                if (products.size() < PAGE_SIZE) {
                    break;
                }
            }
        } catch (BusinessException exception) {
            // Product titles only enrich the ledger; never fail a sync for them.
            return names;
        }
        return names;
    }

    private PlatformTransaction mapOrder(JsonNode order, Map<String, String> productNames) {
        return mapOrder(order, productNames, "COMPLETED");
    }

    private PlatformTransaction mapOrder(
            JsonNode order,
            Map<String, String> productNames,
            String status
    ) {
        String orderId = text(order.path("orderid"));
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        String urlkey = text(order.path("urlkey"));
        BigDecimal amount = decimal(order.path("orderamount"));
        OffsetDateTime occurredAt = time(order.path("ordertime"));
        return new PlatformTransaction(
                orderId,
                "order:" + orderId,
                null,
                null,
                PlatformTransaction.PRODUCT,
                urlkey,
                urlkey == null ? null : productNames.get(urlkey),
                amount,
                "CNY",
                status,
                occurredAt,
                order
        );
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

    private OffsetDateTime time(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        long timestamp = node.asLong(0);
        if (timestamp <= 0) {
            String value = node.asText("");
            try {
                timestamp = Long.parseLong(value.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (timestamp > 10_000_000_000L) {
            return OffsetDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneOffset.UTC
            );
        }
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneOffset.UTC
        );
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() ? null : value.trim();
    }
}
