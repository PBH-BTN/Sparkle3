package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/** A structured order card returned by Afdian, never JSON supplied in ordinary chat text. */
public record AfdianMessageReceipt(String orderId, String sourceType, String sku, BigDecimal amountCny) {
    public static boolean isOrderCard(JsonNode entry) {
        return "receive".equals(entry.path("type").asText())
                && entry.path("message").path("type").asInt(-1) == 2;
    }

    public static AfdianMessageReceipt from(JsonNode entry) {
        if (!isOrderCard(entry)) return null;
        JsonNode content = entry.path("message").path("content");
        String orderId = content.path("out_trade_no").asText("");
        if (!content.isObject() || !orderId.matches("[A-Za-z0-9_-]{1,128}")) return null;
        try {
            BigDecimal amount = new BigDecimal(content.path("total_amount").asText());
            if (amount.signum() < 0 || amount.precision() > 18 || amount.scale() > 2) return null;
            JsonNode details = content.path("sku_detail");
            boolean product = content.path("product_type").asInt(content.path("plan").path("product_type").asInt()) == 1
                    || details.isArray() && !details.isEmpty();
            String sku = content.path("plan").path("plan_id").asText("");
            if (details.isArray()) {
                outer: for (JsonNode item : details) {
                    for (String field : List.of("sku_id", "skuId", "id")) {
                        if (!item.path(field).asText("").isBlank()) {
                            sku = item.path(field).asText();
                            break outer;
                        }
                    }
                }
            }
            if (sku.length() > 255) return null;
            return new AfdianMessageReceipt(orderId, product ? "PRODUCT" : "DONATION", sku.isBlank() ? null : sku, amount);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public String toString() { return "AfdianMessageReceipt[redacted]"; }
}
