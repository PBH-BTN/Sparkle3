package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AfdianClient {
    private final AfdianSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public OrderPage queryOrders(Map<String, Object> params) throws BusinessException {
        if (!settingsService.configured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Afdian API integration is not configured");
        }
        long timestamp = Instant.now().getEpochSecond();
        String compactParams;
        try {
            compactParams = objectMapper.writeValueAsString(params);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to encode Afdian query parameters");
        }
        String userId = settingsService.userId();
        String token = settingsService.accessToken();
        String signSource = token + "params" + compactParams + "ts" + timestamp
                + "user_id" + userId;
        String sign = md5(signSource);
        String body = form(Map.of(
                "user_id", userId,
                "params", compactParams,
                "ts", String.valueOf(timestamp),
                "sign", sign
        ));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(
                            URI.create(settingsService.apiBaseUrl() + "/query-order")
                    )
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("User-Agent", "SparkleBTN-Afdian")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Afdian API URL is invalid: " + exception.getMessage());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to connect to Afdian: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Afdian API request was interrupted");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Afdian API returned HTTP " + response.statusCode());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Afdian returned an invalid JSON response");
        }
        int code = root.path("ec").asInt(
                "200".equals(root.path("ec").asText()) ? 200 : -1
        );
        if (code != 200) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Afdian API error: " + root.path("em").asText("unknown error"));
        }
        JsonNode data = root.path("data");
        List<AfdianOrder> orders = new ArrayList<>();
        for (JsonNode node : data.path("list")) {
            orders.add(parseOrder(node));
        }
        return new OrderPage(
                orders,
                data.path("total_count").asInt(orders.size()),
                data.path("page").asInt(1)
        );
    }

    private AfdianOrder parseOrder(JsonNode node) {
        JsonNode user = node.path("user");
        String afdianUserId = firstText(user, "user_id");
        if (afdianUserId.isBlank()) {
            afdianUserId = firstText(node, "user_private_id");
        }
        String afdianName = firstText(user, "name");
        if (afdianName.isBlank()) {
            afdianName = firstText(node, "user_name");
        }
        String avatar = firstText(user, "avatar");
        if (avatar.isBlank()) {
            avatar = firstText(node, "user_avatar");
        }
        String outTradeNo = firstText(node, "out_trade_no");
        String planTitle = firstText(node, "plan_title");
        if (planTitle.isBlank()) {
            planTitle = node.path("plan").path("title").asText("");
        }
        BigDecimal totalAmount = decimal(node, "total_amount");
        BigDecimal showAmount = decimal(node, "show_amount");
        long createTimestamp = node.path("create_time").asLong(0);
        OffsetDateTime createdAt = createTimestamp > 0
                ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(createTimestamp), ZoneOffset.UTC)
                : null;
        return new AfdianOrder(
                outTradeNo,
                afdianUserId,
                afdianName,
                avatar,
                firstText(node, "plan_id"),
                planTitle,
                totalAmount,
                showAmount,
                node.path("status").asInt(0),
                firstText(node, "remark"),
                createdAt,
                node.deepCopy()
        );
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asText("0");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String firstText(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private String md5(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("MD5 is unavailable", exception);
        }
    }

    private String form(Map<String, String> values) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(values).entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    public record OrderPage(
            List<AfdianOrder> orders,
            int totalCount,
            int page
    ) {
    }

    public record AfdianOrder(
            String outTradeNo,
            String afdianUserId,
            String afdianName,
            String afdianAvatar,
            String planId,
            String planTitle,
            BigDecimal totalAmount,
            BigDecimal showAmount,
            int status,
            String remark,
            OffsetDateTime createdAt,
            JsonNode raw
    ) {
    }
}
