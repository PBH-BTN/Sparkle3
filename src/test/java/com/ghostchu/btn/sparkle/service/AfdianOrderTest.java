package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.btn.sparkle.service.AfdianClient.AfdianOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfdianOrderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sponsorshipPlanIsNotAProduct() {
        AfdianOrder order = order(0, objectMapper.createArrayNode(), "PLAN-A");

        assertFalse(order.isProduct());
        assertEquals("PLAN-A", order.skuId());
    }

    @Test
    void productTypeMarksSoldProduct() {
        ArrayNode skuDetail = objectMapper.createArrayNode();
        skuDetail.add(objectMapper.createObjectNode().put("sku_id", "SKU-1"));

        AfdianOrder order = order(1, skuDetail, "PLAN-B");

        assertTrue(order.isProduct());
        assertEquals("SKU-1", order.skuId());
    }

    @Test
    void variantRowsMarkProductEvenWithoutProductType() {
        ArrayNode skuDetail = objectMapper.createArrayNode();
        ObjectNode variant = objectMapper.createObjectNode();
        variant.put("id", "SKU-2");
        skuDetail.add(variant);

        AfdianOrder order = order(0, skuDetail, null);

        assertTrue(order.isProduct());
        assertEquals("SKU-2", order.skuId());
    }

    @Test
    void productWithoutVariantsFallsBackToPlanId() {
        AfdianOrder order = order(1, objectMapper.createArrayNode(), "PLAN-C");

        assertTrue(order.isProduct());
        assertEquals("PLAN-C", order.skuId());
    }

    private AfdianOrder order(int productType, ArrayNode skuDetail, String planId) {
        return new AfdianOrder(
                "ORDER-1",
                "AFD-1",
                "Sponsor",
                null,
                planId,
                "Product title",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                2,
                null,
                null,
                productType,
                skuDetail,
                objectMapper.createObjectNode()
        );
    }
}
