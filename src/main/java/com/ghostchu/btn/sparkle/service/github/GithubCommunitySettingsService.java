package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GithubCommunitySettingsService {
    public static final String TOKEN_KEY =
            "sparkle.github-community.personal-access-token";
    public static final String API_BASE_URL_KEY =
            "sparkle.github-community.api-base-url";

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;

    public GithubCommunitySettingsService(
            DatabaseConfigService databaseConfigService,
            Environment environment
    ) {
        this.databaseConfigService = databaseConfigService;
        this.environment = environment;
    }

    public String accessToken() {
        return raw(TOKEN_KEY, "");
    }

    public boolean tokenConfigured() {
        return !accessToken().isBlank();
    }

    public String apiBaseUrl() {
        return normalizeApiBaseUrl(raw(API_BASE_URL_KEY, "https://api.github.com"));
    }

    public void update(
            @Nullable String token,
            boolean clearToken,
            String apiBaseUrl,
            @Nullable Long operatorId
    ) throws BusinessException {
        String normalizedBaseUrl = validateApiBaseUrl(apiBaseUrl);
        databaseConfigService.update(API_BASE_URL_KEY, normalizedBaseUrl, operatorId);
        if (clearToken) {
            databaseConfigService.updateSensitive(TOKEN_KEY, "", operatorId);
        } else if (token != null && !token.isBlank()) {
            String normalizedToken = token.trim();
            if (normalizedToken.length() > 4_096) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "GitHub community access token is too long");
            }
            databaseConfigService.updateSensitive(TOKEN_KEY, normalizedToken, operatorId);
        }
    }

    public String validateApiBaseUrl(String value) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "GitHub API base URL is required");
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "GitHub API base URL is invalid");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "GitHub API base URL must be an HTTP(S) URL without query or fragment");
        }
        return normalizeApiBaseUrl(normalized);
    }

    private String normalizeApiBaseUrl(String value) {
        return value.trim().replaceAll("/+$", "");
    }

    private String raw(String key, String fallback) {
        Map<String, String> values = databaseConfigService.getRawValues(List.of(key));
        String value = values.get(key);
        if (value != null) {
            return value;
        }
        return environment.getProperty(key, fallback);
    }
}
