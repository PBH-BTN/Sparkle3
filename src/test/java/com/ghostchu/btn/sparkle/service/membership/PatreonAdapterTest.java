package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatreonAdapterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemberPlatformHttpClient httpClient =
            mock(MemberPlatformHttpClient.class);
    private final PatreonAdapter adapter = new PatreonAdapter(httpClient);

    @Test
    void mapsPatronsToCumulativeDonations() throws Exception {
        stubCampaign();
        when(httpClient.get(contains("/campaigns/c1/members"), eq("token")))
                .thenReturn(objectMapper.readTree("""
                        {
                          "data": [{
                            "type": "member",
                            "id": "m1",
                            "attributes": {
                              "patron_status": "active_patron",
                              "campaign_lifetime_support_cents": 12500,
                              "currently_entitled_amount_cents": 500,
                              "pledge_relationship_start": "2025-01-02T03:04:05+00:00"
                            },
                            "relationships": {
                              "user": {"data": {"type": "user", "id": "u1"}},
                              "currently_entitled_tiers": {
                                "data": [{"type": "tier", "id": "t1"}]
                              }
                            }
                          }],
                          "included": [
                            {
                              "type": "user",
                              "id": "u1",
                              "attributes": {
                                "full_name": "Alice",
                                "email": "Alice@Example.com",
                                "image_url": "https://img.example/a.png"
                              }
                            },
                            {"type": "tier", "id": "t1", "attributes": {"title": "Gold"}}
                          ],
                          "meta": {"pagination": {"cursors": {"next": null}}}
                        }
                        """));

        MemberPlatformSnapshot snapshot = adapter.fetchHistory(credentials());

        assertEquals("c1", snapshot.discoveredAccountId());
        assertEquals(1, snapshot.transactions().size());
        PlatformTransaction transaction = snapshot.transactions().getFirst();
        assertEquals("member:m1", transaction.transactionId());
        assertEquals("alice@example.com", transaction.externalUserId());
        assertEquals("Alice", transaction.externalUserName());
        assertEquals(PlatformTransaction.DONATION, transaction.type());
        assertEquals("t1", transaction.sku());
        assertEquals("Gold", transaction.productName());
        assertEquals(new BigDecimal("125.00"), transaction.amount());
        assertEquals("USD", transaction.currency());
        assertEquals("ACTIVE", transaction.status());
        assertEquals(
                OffsetDateTime.parse("2025-01-02T03:04:05Z"),
                transaction.occurredAt()
        );
    }

    @Test
    void paginatesWithCursors() throws Exception {
        stubCampaign();
        when(httpClient.get(contains("/campaigns/c1/members"), eq("token")))
                .thenReturn(objectMapper.readTree("""
                        {
                          "data": [],
                          "meta": {"pagination": {"cursors": {"next": "abc"}}}
                        }
                        """));
        when(httpClient.get(contains("page%5Bcursor%5D=abc"), eq("token")))
                .thenReturn(objectMapper.readTree("""
                        {"data": [], "meta": {"pagination": {"cursors": {"next": null}}}}
                        """));

        MemberPlatformSnapshot snapshot = adapter.fetchHistory(credentials());

        assertTrue(snapshot.transactions().isEmpty());
    }

    @Test
    void acceptsSignedWebhookPayloads() throws Exception {
        String body = """
                {
                  "data": {
                    "type": "member",
                    "id": "m2",
                    "attributes": {
                      "patron_status": "former_patron",
                      "campaign_lifetime_support_cents": 9900,
                      "pledge_relationship_start": "2025-02-03T00:00:00Z"
                    },
                    "relationships": {"currently_entitled_tiers": {"data": []}}
                  }
                }
                """;
        List<PlatformTransaction> transactions = adapter.parseWebhook(
                credentials("secret"),
                body,
                objectMapper.readTree(body),
                Map.of("x-patreon-signature", signature(body, "secret"))
        );

        assertEquals(1, transactions.size());
        assertEquals("COMPLETED", transactions.getFirst().status());
        assertEquals(new BigDecimal("99.00"), transactions.getFirst().amount());
    }

    @Test
    void rejectsWebhookWithWrongSignature() throws Exception {
        String body = "{\"data\":{\"type\":\"member\",\"id\":\"m2\"}}";
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> adapter.parseWebhook(
                        credentials("secret"),
                        body,
                        objectMapper.readTree(body),
                        Map.of("x-patreon-signature", "00")
                )
        );
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    private void stubCampaign() throws Exception {
        when(httpClient.get(contains("/identity"), eq("token")))
                .thenReturn(objectMapper.readTree("""
                        {
                          "data": {
                            "type": "user",
                            "id": "u1",
                            "relationships": {
                              "campaign": {"data": {"type": "campaign", "id": "c1"}}
                            }
                          },
                          "included": [{
                            "type": "campaign",
                            "id": "c1",
                            "attributes": {"currency": "usd"}
                          }]
                        }
                        """));
    }

    private MemberPlatformCredentials credentials() {
        return credentials(null);
    }

    private MemberPlatformCredentials credentials(String webhookSecret) {
        return new MemberPlatformCredentials(
                MemberPlatform.PATREON,
                "token",
                null,
                webhookSecret,
                "USD",
                BigDecimal.ONE
        );
    }

    private String signature(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacMD5");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacMD5"
        ));
        return HexFormat.of().formatHex(
                mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
        );
    }
}
