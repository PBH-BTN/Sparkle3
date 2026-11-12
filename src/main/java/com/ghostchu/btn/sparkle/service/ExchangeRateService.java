package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.CurrencyExchangeRate;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.CurrencyExchangeRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {
    public static final String BASE_CURRENCY_KEY = "sparkle.memberships.base-currency";
    public static final String RATE_API_URL_KEY =
            "sparkle.memberships.exchange-rate-api-url";
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final CurrencyExchangeRateMapper exchangeRateMapper;
    private final ObjectMapper objectMapper;
    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String baseCurrency() {
        return normalizeCurrency(raw(BASE_CURRENCY_KEY, "CNY"));
    }

    public String apiUrl() {
        return raw(
                RATE_API_URL_KEY,
                "https://open.er-api.com/v6/latest/{currency}"
        ).trim();
    }

    public void updateSettings(
            String baseCurrency,
            String apiUrl,
            @Nullable Long operatorId
    ) throws BusinessException {
        String normalizedCurrency = validateCurrency(baseCurrency);
        String normalizedUrl = validateApiUrl(apiUrl);
        databaseConfigService.update(
                BASE_CURRENCY_KEY,
                normalizedCurrency,
                operatorId
        );
        databaseConfigService.update(
                RATE_API_URL_KEY,
                normalizedUrl,
                operatorId
        );
    }

    public BigDecimal convert(
            BigDecimal amount,
            String fromCurrency
    ) throws BusinessException {
        BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount;
        String base = baseCurrency();
        String quote = validateCurrency(fromCurrency);
        if (base.equals(quote)) {
            return normalizedAmount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rate = rateToBase(base, quote);
        return normalizedAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public void refresh() throws BusinessException {
        refreshRates(baseCurrency());
    }

    private BigDecimal rateToBase(String base, String quote)
            throws BusinessException {
        List<CachedRate> cached = cachedRates(base, quote);
        boolean stale = cached.isEmpty()
                || cached.getFirst().fetchedAt() == null
                || cached.getFirst().fetchedAt().plus(CACHE_TTL)
                .isBefore(OffsetDateTime.now());
        if (stale) {
            try {
                refreshRates(base);
                cached = cachedRates(base, quote);
            } catch (BusinessException exception) {
                if (cached.isEmpty()) {
                    throw exception;
                }
                log.warn("Using stale exchange rate for {} -> {}: {}",
                        quote, base, exception.getMessage());
            }
        }
        if (cached.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "No exchange rate is available for " + quote + " to " + base);
        }
        return cached.getFirst().rateToBase();
    }

    private synchronized void refreshRates(String base) throws BusinessException {
        String url = apiUrl().replace("{currency}", base);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SparkleBTN-ExchangeRates")
                    .GET()
                    .build();
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Exchange rate API URL is invalid");
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to fetch exchange rates: " + exception.getMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Exchange rate API returned HTTP " + response.statusCode());
        }
        JsonNode rates;
        try {
            rates = objectMapper.readTree(response.body()).path("rates");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Exchange rate API returned invalid JSON");
        }
        if (!rates.isObject()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Exchange rate API response does not include rates");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = rates.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            BigDecimal providerRate = entry.getValue().decimalValue();
            if (providerRate.signum() <= 0) continue;
            BigDecimal quoteToBase = BigDecimal.ONE.divide(
                    providerRate,
                    12,
                    RoundingMode.HALF_UP
            );
            String quote = normalizeCurrency(entry.getKey());
            exchangeRateMapper.upsert(new CurrencyExchangeRate()
                    .setBaseCurrency(base)
                    .setQuoteCurrency(quote)
                    .setRateToBase(quoteToBase)
                    .setFetchedAt(OffsetDateTime.now())
                    .setSource(url));
        }
        exchangeRateMapper.upsert(new CurrencyExchangeRate()
                .setBaseCurrency(base)
                .setQuoteCurrency(base)
                .setRateToBase(BigDecimal.ONE)
                .setFetchedAt(OffsetDateTime.now())
                .setSource(url));
    }

    private List<CachedRate> cachedRates(String base, String quote) {
        return exchangeRateMapper.selectList(
                new QueryWrapper<CurrencyExchangeRate>()
                        .eq("base_currency", base)
                        .eq("quote_currency", quote)
        ).stream()
                .map(rate -> new CachedRate(rate.getRateToBase(), rate.getFetchedAt()))
                .toList();
    }

    public String validateCurrency(String value) throws BusinessException {
        String normalized = normalizeCurrency(value);
        if (!normalized.matches("[A-Z]{3,8}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Currency code must contain 3 to 8 letters");
        }
        return normalized;
    }

    private String validateApiUrl(String value) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.contains("{currency}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Exchange rate API URL must include the {currency} placeholder");
        }
        URI uri;
        try {
            uri = URI.create(normalized.replace("{currency}", "USD"));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Exchange rate API URL is invalid");
        }
        if (uri.getScheme() == null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Exchange rate API URL must be HTTP(S)");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String raw(String key, String fallback) {
        Map<String, String> values = databaseConfigService.getRawValues(List.of(key));
        String value = values.get(key);
        return value != null ? value : environment.getProperty(key, fallback);
    }

    private record CachedRate(BigDecimal rateToBase, OffsetDateTime fetchedAt) {
    }
}
