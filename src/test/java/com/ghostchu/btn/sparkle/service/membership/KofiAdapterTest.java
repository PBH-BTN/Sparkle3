package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KofiAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KofiAdapter adapter = new KofiAdapter();

    @Test
    void mapsShopOrderToSingleProductTransaction() throws Exception {
        String body = """
                {
                  "verification_token": "token",
                  "message_id": "msg-1",
                  "kofi_transaction_id": "txn-1",
                  "type": "Shop Order",
                  "from_name": "Bob",
                  "email": "Bob@Example.com",
                  "currency": "USD",
                  "amount": "19.50",
                  "timestamp": "2025-03-04T05:06:07Z",
                  "shop_items": [
                    {"direct_link_code": "sku-a", "variation_name": "Theme A", "quantity": 1},
                    {"direct_link_code": "sku-b", "variation_name": "Theme B", "quantity": 1}
                  ]
                }
                """;

        List<PlatformTransaction> transactions = adapter.parseWebhook(
                credentials(),
                body,
                objectMapper.readTree(body),
                Map.of()
        );

        assertEquals(1, transactions.size());
        PlatformTransaction transaction = transactions.getFirst();
        assertEquals("txn-1", transaction.transactionId());
        assertEquals("bob@example.com", transaction.externalUserId());
        assertEquals(PlatformTransaction.PRODUCT, transaction.type());
        assertEquals("sku-a,sku-b", transaction.sku());
        assertEquals("Theme A,Theme B", transaction.productName());
        assertEquals(new BigDecimal("19.50"), transaction.amount());
        assertEquals("USD", transaction.currency());
    }

    @Test
    void mapsSubscriptionPaymentToDonation() throws Exception {
        String body = """
                {
                  "verification_token": "token",
                  "message_id": "msg-2",
                  "kofi_transaction_id": "txn-2",
                  "type": "Subscription",
                  "is_subscription_payment": true,
                  "is_first_subscription_payment": false,
                  "tier_name": "Gold",
                  "from_name": "Carol",
                  "email": "carol@example.com",
                  "currency": "GBP",
                  "amount": "5.00",
                  "timestamp": "2025-04-05T00:00:00Z"
                }
                """;

        List<PlatformTransaction> transactions = adapter.parseWebhook(
                credentials(),
                body,
                objectMapper.readTree(body),
                Map.of()
        );

        PlatformTransaction transaction = transactions.getFirst();
        assertEquals(PlatformTransaction.DONATION, transaction.type());
        assertEquals("Gold", transaction.sku());
        assertEquals("GBP", transaction.currency());
        assertEquals("carol@example.com", transaction.externalUserId());
    }

    @Test
    void rejectsInvalidVerificationToken() throws Exception {
        String body = "{\"verification_token\":\"wrong\",\"kofi_transaction_id\":\"x\"}";
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adapter.parseWebhook(
                        credentials(),
                        body,
                        objectMapper.readTree(body),
                        Map.of()
                )
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void hasNoHistoryApi() {
        assertFalse(adapter.supportsHistorySync());
        assertEquals(List.of("webhookToken"), adapter.configFields());
    }

    private MemberPlatformCredentials credentials() {
        return new MemberPlatformCredentials(
                MemberPlatform.KOFI,
                null,
                null,
                "token",
                "CNY",
                BigDecimal.ONE
        );
    }
}
