package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MianbaoduoAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemberPlatformHttpClient httpClient =
            mock(MemberPlatformHttpClient.class);
    private final MianbaoduoAdapter adapter = new MianbaoduoAdapter(httpClient);

    @Test
    void mapsOrdersToProductTransactions() throws Exception {
        stubProducts();
        when(httpClient.get(contains("/order-list"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {
                          "code": 200,
                          "result": {
                            "count": 2,
                            "previous_page": null,
                            "next_page": null,
                            "orders": [
                              {
                                "ordertime": 1569739766,
                                "orderamount": 0.01,
                                "payway": "wx",
                                "orderid": "o1",
                                "creatorid": "c1",
                                "urlkey": "k1"
                              },
                              {
                                "ordertime": 1569739999,
                                "orderamount": 12.5,
                                "payway": "alipay",
                                "orderid": "o2",
                                "creatorid": "c1",
                                "urlkey": "k2"
                              }
                            ]
                          },
                          "error_info": ""
                        }
                        """));

        MemberPlatformSnapshot snapshot = adapter.fetchHistory(credentials());

        assertEquals(2, snapshot.transactions().size());
        PlatformTransaction first = snapshot.transactions().getFirst();
        assertEquals("o1", first.transactionId());
        assertEquals("order:o1", first.externalUserId());
        assertEquals(PlatformTransaction.PRODUCT, first.type());
        assertEquals("k1", first.sku());
        assertEquals("作品 A", first.productName());
        assertEquals(new BigDecimal("0.01"), first.amount());
        assertEquals("CNY", first.currency());
        assertEquals("COMPLETED", first.status());
        assertEquals(
                OffsetDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(1569739766),
                        java.time.ZoneOffset.UTC
                ),
                first.occurredAt()
        );
    }

    @Test
    void resolvesOrderProofAgainstTheApi() throws Exception {
        stubProducts();
        when(httpClient.get(contains("/order-detail?order_id=o9"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {
                          "code": 200,
                          "result": {
                            "ordertime": 1590508123,
                            "orderamount": 3,
                            "orderid": "o9",
                            "state": "success",
                            "urlkey": "k1"
                          },
                          "error_info": ""
                        }
                        """));

        PlatformTransaction transaction = adapter.resolveProof(credentials(), "o9");

        assertNotNull(transaction);
        assertEquals("o9", transaction.transactionId());
        assertEquals("order:o9", transaction.externalUserId());
        assertEquals("success", transaction.status());
        assertEquals(new BigDecimal("3"), transaction.amount());
    }

    @Test
    void skipsUnpaidOrders() throws Exception {
        stubProducts();
        when(httpClient.get(contains("/order-detail"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {
                          "code": 200,
                          "result": {
                            "orderid": "o9",
                            "state": "cancel",
                            "orderamount": 3
                          },
                          "error_info": ""
                        }
                        """));

        assertNull(adapter.resolveProof(credentials(), "o9"));
    }

    @Test
    void skipsIncludedBundleItemsWithZeroAmount() throws Exception {
        stubProducts();
        when(httpClient.get(contains("/order-list"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {
                          "code": 200,
                          "result": {
                            "count": 2,
                            "orders": [
                              {"ordertime": 1569739766, "orderamount": 0,
                               "orderid": "included", "urlkey": "k1"},
                              {"ordertime": 1569739767, "orderamount": 30,
                               "orderid": "bundle", "urlkey": "k9"}
                            ]
                          },
                          "error_info": ""
                        }
                        """));

        MemberPlatformSnapshot snapshot = adapter.fetchHistory(credentials());

        assertEquals(1, snapshot.transactions().size());
        assertEquals("bundle", snapshot.transactions().getFirst().transactionId());
    }

    @Test
    void rejectsInvalidDeveloperKey() throws Exception {
        when(httpClient.get(contains("/order-list"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {"code": 403, "result": null, "error_info": "forbidden"}
                        """));
        when(httpClient.get(contains("/product-list"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {"code": 200, "result": {"count": 0, "products": []}}
                        """));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adapter.fetchHistory(credentials())
        );
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    private void stubProducts() throws Exception {
        when(httpClient.get(contains("/product-list"), eq(null), any()))
                .thenReturn(objectMapper.readTree("""
                        {
                          "code": 200,
                          "result": {
                            "count": 1,
                            "products": [{"urlkey": "k1", "productname": "作品 A"}]
                          },
                          "error_info": ""
                        }
                        """));
    }

    private MemberPlatformCredentials credentials() {
        return new MemberPlatformCredentials(
                MemberPlatform.MIANBAODUO,
                "developer-key",
                null,
                null,
                "CNY",
                BigDecimal.ONE
        );
    }
}
