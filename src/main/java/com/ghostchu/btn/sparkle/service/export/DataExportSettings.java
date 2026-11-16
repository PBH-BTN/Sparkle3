package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Settings;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataExportSettings {
    private static final String PREFIX = "sparkle.exports.";
    private static final List<String> KEYS = List.of("max-rows", "max-file-mib", "retention-hours",
            "download-mbps", "daily-slots", "pool-mib", "prompt-zh", "prompt-en").stream()
            .map(PREFIX::concat).toList();
    private final DatabaseConfigService config;

    public Settings get() {
        Map<String, String> values = new LinkedHashMap<>(config.getRawValues(KEYS));
        for (String key : KEYS) {
            String override = config.getExternalOverride(key, values.get(key));
            if (override != null) values.put(key, override);
        }
        return new Settings(number(values, "max-rows", 1_000_000), number(values, "max-file-mib", 15_360),
                number(values, "retention-hours", 72), number(values, "download-mbps", 300),
                number(values, "daily-slots", 5), number(values, "pool-mib", 102_400),
                values.getOrDefault(PREFIX + "prompt-zh", ""), values.getOrDefault(PREFIX + "prompt-en", ""));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Settings values, Long operatorId) throws BusinessException {
        if (!valid(values.maxRows()) || !valid(values.maxFileMiB()) || !valid(values.retentionHours())
                || !valid(values.downloadMbps()) || !valid(values.dailySlots()) || !valid(values.poolMiB())
                || values.poolMiB() < values.maxFileMiB() || !validPrompt(values.promptZh())
                || !validPrompt(values.promptEn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "export_invalid_settings");
        }
        List<String> updated = List.of(values.maxRows().toString(), values.maxFileMiB().toString(),
                values.retentionHours().toString(), values.downloadMbps().toString(), values.dailySlots().toString(),
                values.poolMiB().toString(), values.promptZh(), values.promptEn());
        for (int i = 0; i < KEYS.size(); i++) {
            config.update(KEYS.get(i), updated.get(i), operatorId);
        }
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        try {
            int value = Integer.parseInt(values.get(PREFIX + key));
            return valid(value) ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean valid(Integer value) {
        return value != null && value > 0;
    }

    private static boolean validPrompt(String prompt) {
        return prompt != null && !prompt.isBlank() && prompt.length() <= 4_000;
    }
}
