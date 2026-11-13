package com.ghostchu.btn.sparkle.service.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Small JSON HTTP client shared by membership platform adapters.
 *
 * <p>Adapters only need paginated GET requests with bearer credentials, so the
 * surface stays narrow: fixed timeouts, a bounded response body, explicit
 * status handling, and no request or response logging.</p>
 */
@Component
public class MemberPlatformHttpClient {
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MemberPlatformHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String url, String bearerToken) throws BusinessException {
        return get(url, bearerToken, Map.of());
    }

    public JsonNode get(
            String url,
            String bearerToken,
            Map<String, String> headers
    ) throws BusinessException {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SparkleBTN-MemberPlatforms");
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Member platform API URL is invalid");
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        headers.forEach(builder::header);
        builder.GET();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Member platform API request was interrupted");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to reach the member platform API");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "The member platform rejected the configured credential");
        }
        if (response.statusCode() == 429) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "The member platform rate limit was reached; retry later");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "The member platform API returned HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "The member platform API returned an empty response");
        }
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "The member platform API response is too large");
        }
        try {
            return objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "The member platform API returned invalid JSON");
        }
    }
}
