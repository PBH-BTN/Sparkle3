package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GithubSyncSettingsService {
    public static final String SCHEDULE_KEY = "sparkle.github-sync.schedule";
    public static final String TOKEN_KEY = "sparkle.github-sync.personal-access-token";
    public static final String LEGACY_OWNER_KEY = "sparkle.github-sync.org-name";
    public static final String LEGACY_REPOSITORY_KEY = "sparkle.github-sync.repository-name";
    public static final String LEGACY_BRANCH_KEY = "sparkle.github-sync.branch-name";
    public static final String TARGETS_INITIALIZED_KEY =
            "sparkle.github-sync.targets-initialized";

    private final DatabaseConfigService databaseConfigService;
    private final Environment environment;

    public GithubSyncSettingsService(
            DatabaseConfigService databaseConfigService,
            Environment environment
    ) {
        this.databaseConfigService = databaseConfigService;
        this.environment = environment;
    }

    public String schedule() {
        return raw(SCHEDULE_KEY, "0 30 * * * *");
    }

    public String accessToken() {
        return raw(TOKEN_KEY, "");
    }

    public boolean tokenConfigured() {
        return !accessToken().isBlank();
    }

    public LegacyTargetDefaults legacyTargetDefaults() {
        return new LegacyTargetDefaults(
                raw(LEGACY_OWNER_KEY, "PBH-BTN"),
                raw(LEGACY_REPOSITORY_KEY, "BTN-Collected-Rules"),
                raw(LEGACY_BRANCH_KEY, "main")
        );
    }

    public void update(
            String schedule,
            @Nullable String token,
            boolean clearToken,
            @Nullable Long operatorId
    ) throws BusinessException {
        normalizeSchedule(schedule);
        databaseConfigService.update(SCHEDULE_KEY, schedule, operatorId);
        if (clearToken) {
            databaseConfigService.updateSensitive(TOKEN_KEY, "", operatorId);
        } else if (token != null && !token.isBlank()) {
            databaseConfigService.updateSensitive(TOKEN_KEY, token.trim(), operatorId);
        }
    }

    public void validateSchedule(String schedule) throws BusinessException {
        normalizeSchedule(schedule);
    }

    private String normalizeSchedule(String schedule) throws BusinessException {
        if (schedule == null || schedule.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GitHub sync schedule is required");
        }
        String normalized = schedule.trim();
        if ("-".equals(normalized)) {
            return normalized;
        }
        try {
            CronExpression.parse(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid GitHub sync Spring cron expression"
            );
        }
    }

    private String raw(String key, String fallback) {
        Map<String, String> values = databaseConfigService.getRawValues(List.of(key));
        String value = values.get(key);
        return value != null ? value : environment.getProperty(key, fallback);
    }

    public record LegacyTargetDefaults(
            String owner,
            String repository,
            String branch
    ) {
    }
}
