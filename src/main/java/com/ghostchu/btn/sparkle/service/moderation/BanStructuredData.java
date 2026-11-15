package com.ghostchu.btn.sparkle.service.moderation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class BanStructuredData {
    private BanStructuredData() {}

    public static Map<String, Object> parse(ObjectMapper mapper, String data) {
        if (data == null || data.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = mapper.readValue(data, new TypeReference<>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return Map.of();
        }
    }
}
