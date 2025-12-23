package com.ghostchu.btn.sparkle.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Globalping API 客户端工具类
 * 基于 Spring RestClient 封装
 */
public class GlobalpingApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    /**
     * -- SETTER --
     *  设置访问令牌
     */
    @Setter
    private String accessToken;

    /**
     * 构造函数
     *
     * @param baseUrl API 基础 URL，默认为 <a href="https://api.globalping.io">...</a>
     */
    public GlobalpingApiClient(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.globalping.io";
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, buildUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .build();
    }

    public GlobalpingApiClient() {
        this("https://api.globalping.io");
    }

    /**
     * 构建 User-Agent 字符串
     */
    private String buildUserAgent() {
        return "SparkleBTN/3.0 GlobalpingApiClient/1.0 (Java/" + System.getProperty("java.version") + ")";
    }

    /**
     * 构建认证头
     */
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }

    // ==================== 测量相关方法 ====================

    /**
     * 创建测量
     */
    public CreateMeasurementResponse createMeasurement(MeasurementRequest request) {
        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            
            return restClient.post()
                    .uri("/v1/measurements")
                    .headers(headers -> headers.putAll(buildAuthHeaders()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                            (req, res) -> handleError(res))
                    .body(CreateMeasurementResponse.class);
        } catch (Exception e) {
            throw new GlobalpingApiException("Failed to create measurement", e);
        }
    }

    /**
     * 获取测量结果
     */
    public MeasurementResponse getMeasurement(String measurementId) {
        return restClient.get()
                .uri("/v1/measurements/{id}", measurementId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        (req, res) -> handleError(res))
                .body(MeasurementResponse.class);
    }

    /**
     * 等待并获取最终测量结果（遵循客户端指南中的轮询机制）
     */
    public MeasurementResponse waitForMeasurementResult(String measurementId, Duration timeout) {
        Instant start = Instant.now();
        AtomicReference<String> lastEtag = new AtomicReference<>();
        
        while (Duration.between(start, Instant.now()).compareTo(timeout) < 0) {
            try {
                // 构建请求，包含 ETag 缓存
                RestClient.RequestHeadersSpec<?> request = restClient.get()
                        .uri("/v1/measurements/{id}", measurementId);
                
                if (lastEtag.get() != null) {
                    request.header(HttpHeaders.IF_NONE_MATCH, lastEtag.get());
                }
                
                ResponseEntity<MeasurementResponse> response = request
                        .retrieve()
                        .toEntity(MeasurementResponse.class);
                
                // 更新 ETag
                String etag = response.getHeaders().getFirst(HttpHeaders.ETAG);
                if (etag != null) {
                    lastEtag.set(etag);
                }
                
                // 如果是 304 Not Modified，继续等待
                if (response.getStatusCode().value() == 304) {
                    Thread.sleep(500);
                    continue;
                }
                
                MeasurementResponse measurement = response.getBody();
                if (measurement == null) {
                    Thread.sleep(500);
                    continue;
                }
                
                // 检查测量状态
                if (!"in-progress".equals(measurement.getStatus())) {
                    return measurement; // 测量完成
                }
                
                // 根据指南，等待500毫秒
                Thread.sleep(500);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GlobalpingApiException("Measurement polling interrupted", e);
            } catch (Exception e) {
                throw new GlobalpingApiException("Failed to get measurement result", e);
            }
        }
        
        throw new GlobalpingApiException("Measurement timeout after " + timeout);
    }

    // ==================== 探针相关方法 ====================

    /**
     * 获取当前在线探针列表
     */
    public List<Probe> listProbes() {
        return restClient.get()
                .uri("/v1/probes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ==================== 限制相关方法 ====================

    /**
     * 获取当前速率限制信息
     */
    public Limits getLimits() {
        return restClient.get()
                .uri("/v1/limits")
                .headers(headers -> headers.putAll(buildAuthHeaders()))
                .retrieve()
                .body(Limits.class);
    }

    // ==================== 错误处理 ====================

    private void handleError(org.springframework.http.client.ClientHttpResponse response) {
        try {
            Map<String, Object> errorResponse = objectMapper.readValue(
                    response.getBody(),
                    new TypeReference<>() {
                    }
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) errorResponse.get("error");
            String type = (String) error.get("type");
            String message = (String) error.get("message");
            
            int statusCode = response.getStatusCode().value();
            
            switch (statusCode) {
                case 400:
                    throw new BadRequestException(message, type);
                case 404:
                    throw new NotFoundException(message, type);
                case 422:
                    throw new UnprocessableEntityException(message, type);
                case 429:
                    // 获取限流相关头信息
                    Map<String, String> rateLimitHeaders = new HashMap<>();
                    response.getHeaders().forEach((key, values) -> {
                        if (key.toLowerCase().startsWith("x-ratelimit-") || 
                            key.toLowerCase().startsWith("x-credits-") ||
                            key.equals("retry-after")) {
                            rateLimitHeaders.put(key, values.getFirst());
                        }
                    });
                    throw new RateLimitExceededException(message, type, rateLimitHeaders);
                default:
                    throw new GlobalpingApiException(
                            String.format("API error %d: %s - %s", statusCode, type, message)
                    );
            }
        } catch (Exception e) {
            if (e instanceof GlobalpingApiException) {
                throw (GlobalpingApiException) e;
            }
            throw new GlobalpingApiException("Failed to parse error response", e);
        }
    }

    // ==================== 请求/响应模型 ====================

    // 测量请求
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MeasurementRequest {
        private String type;
        private String target;
        
        @lombok.Builder.Default
        private Boolean inProgressUpdates = false;
        
        private Object locations; // 可以是 List<MeasurementLocationOption> 或 String
        private Integer limit;
        private Object measurementOptions; // 根据 type 不同而不同
    }

    // 测量位置选项
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MeasurementLocationOption {
        private String continent;
        private String region;
        private String country;
        private String state;
        private String city;
        private Integer asn;
        private String network;
        private List<String> tags;
        private String magic;
        private Integer limit;
    }

    // Ping 选项
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PingOptions {
        @lombok.Builder.Default
        private Integer packets = 3;
        
        @lombok.Builder.Default
        private String protocol = "ICMP";
        
        @lombok.Builder.Default
        private Integer port = 80;
        
        private Integer ipVersion;
    }

    // DNS 选项
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DnsOptions {
        private DnsQuery query;
        private String resolver;
        
        @lombok.Builder.Default
        private String protocol = "UDP";
        
        @lombok.Builder.Default
        private Integer port = 53;
        
        private Integer ipVersion;
        
        @lombok.Builder.Default
        private Boolean trace = false;
        
        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class DnsQuery {
            @lombok.Builder.Default
            private String type = "A";
        }
    }

    // HTTP 选项
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HttpOptions {
        private HttpRequest request;
        private String resolver;
        
        @lombok.Builder.Default
        private String protocol = "HTTPS";
        
        @lombok.Builder.Default
        private Integer port = 80;
        
        private Integer ipVersion;
        
        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class HttpRequest {
            private String host;
            private String path;
            private String query;
            
            @lombok.Builder.Default
            private String method = "HEAD";
            
            private Map<String, String> headers;
        }
    }

    // 创建测量响应
    @lombok.Data
    public static class CreateMeasurementResponse {
        private String id;
        private Integer probesCount;
    }

    // 测量响应
    @lombok.Data
    public static class MeasurementResponse {
        private String id;
        private String type;
        private String status;
        private String target;
        private String createdAt;
        private String updatedAt;
        private Integer probesCount;
        private List<MeasurementLocationOption> locations;
        private Integer limit;
        private Object measurementOptions;
        private List<MeasurementResultItem> results;
    }

    // 测量结果项
    @lombok.Data
    public static class MeasurementResultItem {
        private Probe probe;
        private Object result; // 根据测量类型不同而不同
    }

    // 探针信息
    @lombok.Data
    public static class Probe {
        private String version;
        private ProbeLocation location;
        private List<String> tags;
        private List<String> resolvers;
    }

    // 探针位置
    @lombok.Data
    public static class ProbeLocation {
        private String continent;
        private String region;
        private String country;
        private String state;
        private String city;
        private Integer asn;
        private String network;
        private Double latitude;
        private Double longitude;
    }

    // 速率限制信息
    @lombok.Data
    public static class Limits {
        private RateLimit rateLimit;
        private Credits credits;
        
        @lombok.Data
        public static class RateLimit {
            private Measurements measurements;
            
            @lombok.Data
            public static class Measurements {
                private RateLimitDetails create;
            }
        }
        
        @lombok.Data
        public static class Credits {
            private Integer remaining;
        }
    }

    // 速率限制详情
    @lombok.Data
    public static class RateLimitDetails {
        private String type;
        private Integer limit;
        private Integer remaining;
        private Integer reset;
    }

    // ==================== 异常类 ====================

    public static class GlobalpingApiException extends RuntimeException {
        public GlobalpingApiException(String message) {
            super(message);
        }
        
        public GlobalpingApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    @Getter
    public static class BadRequestException extends GlobalpingApiException {
        private final String errorType;
        
        public BadRequestException(String message, String errorType) {
            super("Bad Request: " + message);
            this.errorType = errorType;
        }

    }
    
    @Getter
    public static class NotFoundException extends GlobalpingApiException {
        private final String errorType;
        
        public NotFoundException(String message, String errorType) {
            super("Not Found: " + message);
            this.errorType = errorType;
        }

    }
    
    @Getter
    public static class UnprocessableEntityException extends GlobalpingApiException {
        private final String errorType;
        
        public UnprocessableEntityException(String message, String errorType) {
            super("Unprocessable Entity: " + message);
            this.errorType = errorType;
        }

    }
    
    @Getter
    public static class RateLimitExceededException extends GlobalpingApiException {
        private final String errorType;
        private final Map<String, String> rateLimitHeaders;
        
        public RateLimitExceededException(String message, String errorType, Map<String, String> rateLimitHeaders) {
            super("Rate Limit Exceeded: " + message);
            this.errorType = errorType;
            this.rateLimitHeaders = rateLimitHeaders;
        }

        public Integer getRetryAfterSeconds() {
            String retryAfter = rateLimitHeaders.get("retry-after");
            return retryAfter != null ? Integer.parseInt(retryAfter) : null;
        }
        
        public Integer getRemainingRequests() {
            String remaining = rateLimitHeaders.get("x-ratelimit-remaining");
            return remaining != null ? Integer.parseInt(remaining) : null;
        }
    }

    // ==================== 使用示例 ====================

    public static void main(String[] args) {
        // 创建客户端
        GlobalpingApiClient client = new GlobalpingApiClient();
        
        // 如果有访问令牌，可以设置
        // client.setAccessToken("your-token-here");
        
        try {
            // 示例1: 创建 Ping 测量
            MeasurementRequest pingRequest = MeasurementRequest.builder()
                    .type("ping")
                    .target("cdn.jsdelivr.net")
                    .locations(Arrays.asList(
                            MeasurementLocationOption.builder()
                                    .country("DE")
                                    .build(),
                            MeasurementLocationOption.builder()
                                    .country("PL")
                                    .build()
                    ))
                    .measurementOptions(PingOptions.builder()
                            .packets(3)
                            .build())
                    .build();
            
            CreateMeasurementResponse pingResponse = client.createMeasurement(pingRequest);
            System.out.println("Created ping measurement: " + pingResponse.getId());
            
            // 等待并获取结果
            MeasurementResponse result = client.waitForMeasurementResult(
                    pingResponse.getId(), 
                    Duration.ofSeconds(30)
            );
            System.out.println("Ping measurement status: " + result.getStatus());
            
            // 示例2: 创建 DNS 测量
            MeasurementRequest dnsRequest = MeasurementRequest.builder()
                    .type("dns")
                    .target("cdn.jsdelivr.net")
                    .measurementOptions(DnsOptions.builder()
                            .query(DnsOptions.DnsQuery.builder()
                                    .type("A")
                                    .build())
                            .trace(true)
                            .build())
                    .limit(2)
                    .build();
            
            CreateMeasurementResponse dnsResponse = client.createMeasurement(dnsRequest);
            System.out.println("Created DNS measurement: " + dnsResponse.getId());
            
            // 示例3: 获取在线探针
            List<Probe> probes = client.listProbes();
            System.out.println("Online probes count: " + probes.size());
            
            // 示例4: 获取速率限制
            Limits limits = client.getLimits();
            System.out.println("Rate limit remaining: " + 
                    limits.getRateLimit().getMeasurements().getCreate().getRemaining());
            
        } catch (RateLimitExceededException e) {
            System.err.println("Rate limit exceeded. Retry after: " + 
                    e.getRetryAfterSeconds() + " seconds");
        } catch (GlobalpingApiException e) {
            System.err.println("API error: " + e.getMessage());
        }
    }
}