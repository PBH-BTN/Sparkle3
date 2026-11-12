package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.FlarumIntegrationAdminModels.SettingsView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FlarumIntegrationSettingsService {
    public static final String TOKEN_KEY = "sparkle.flarum-integration.token";
    private static final String CONFIG_GROUP = "sparkle.flarum-integration";
    private static final int MIN_TOKEN_LENGTH = 16;

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;

    public FlarumIntegrationSettingsService(
            DatabaseConfigService databaseConfigService,
            Environment environment
    ) {
        this.databaseConfigService = databaseConfigService;
        this.environment = environment;
    }

    public String token() {
        Map<String, String> values = databaseConfigService.getRawValues(List.of(TOKEN_KEY));
        if (values != null && values.containsKey(TOKEN_KEY)) {
            String value = values.get(TOKEN_KEY);
            return value == null ? "" : value.trim();
        }
        return environment.getProperty(TOKEN_KEY, "").trim();
    }

    public boolean tokenConfigured() {
        return !token().isBlank();
    }

    public SettingsView view() {
        var entry = databaseConfigService.list(null, CONFIG_GROUP).stream()
                .filter(item -> TOKEN_KEY.equals(item.key()))
                .findFirst()
                .orElse(null);
        return new SettingsView(
                tokenConfigured(),
                entry == null ? null : entry.updatedAt(),
                entry == null ? null : entry.updatedBy()
        );
    }

    public SettingsView update(
            @Nullable String token,
            boolean clearToken,
            @Nullable Long operatorId
    ) throws BusinessException {
        if (!databaseConfigService.exists(TOKEN_KEY)) {
            databaseConfigService.insertIfMissing(TOKEN_KEY, "", "database");
        }

        if (clearToken) {
            databaseConfigService.updateSensitive(TOKEN_KEY, "", operatorId);
            return view();
        }

        String normalized = token == null ? "" : token.trim();
        if (normalized.length() < MIN_TOKEN_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Flarum integration token must contain at least "
                            + MIN_TOKEN_LENGTH
                            + " characters"
            );
        }
        databaseConfigService.updateSensitive(TOKEN_KEY, normalized, operatorId);
        return view();
    }
}
