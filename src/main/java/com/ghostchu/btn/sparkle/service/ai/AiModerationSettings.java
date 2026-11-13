package com.ghostchu.btn.sparkle.service.ai;

public record AiModerationSettings(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        String rubric,
        int timeoutSeconds,
        int maxAttempts,
        boolean moderateAvatars
) {
    public boolean apiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
