package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class AfdianMessageReceiptTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsStructuredReceivedOrderCardsIncludingSkuAndAmount() throws Exception {
        var entry = json.readTree("""
                {"type":"receive","message":{"type":2,"content":{
                  "out_trade_no":"order-1","total_amount":"68.00","product_type":1,
                  "sku_detail":[{"sku_id":"sku-1","price":"68.00","count":1}],
                  "plan":{"plan_id":"plan-1"}}}}
                """);
        var receipt = AfdianMessageReceipt.from(entry);
        assertNotNull(receipt);
        assertEquals("order-1", receipt.orderId());
        assertEquals("PRODUCT", receipt.sourceType());
        assertEquals("sku-1", receipt.sku());
        assertEquals(new BigDecimal("68.00"), receipt.amountCny());
    }

    @Test
    void sponsorshipCardsUsePlanAndDoNotRequireProductSkus() throws Exception {
        var receipt = AfdianMessageReceipt.from(json.readTree("""
                {"type":"receive","message":{"type":2,"content":{
                  "out_trade_no":"donation-1","total_amount":"50.00","product_type":0,
                  "sku_detail":[],"plan":{"plan_id":"plan-1"}}}}
                """));
        assertNotNull(receipt);
        assertEquals("DONATION", receipt.sourceType());
        assertEquals("plan-1", receipt.sku());
    }

    @Test
    void ignoresOrdinaryChatEvenWhenItContainsOrderShapedJson() throws Exception {
        assertNull(AfdianMessageReceipt.from(json.readTree("""
                {"type":"receive","message":{"type":4,"content":{
                  "out_trade_no":"order-1","total_amount":"50.00"}}}
                """)));
        assertNull(AfdianMessageReceipt.from(json.readTree("""
                {"type":"send","message":{"type":2,"content":{
                  "out_trade_no":"order-1","total_amount":"50.00"}}}
                """)));
    }
}
