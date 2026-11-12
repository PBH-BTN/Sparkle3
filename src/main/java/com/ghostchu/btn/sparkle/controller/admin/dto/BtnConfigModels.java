package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class BtnConfigModels {
    private BtnConfigModels() {
    }

    public record BtnConfigFieldView(
            String key,
            String section,
            String field,
            String valueType,
            String value,
            String defaultValue,
            Long min,
            Long max,
            boolean hotReload,
            boolean environmentOverride,
            String source,
            OffsetDateTime updatedAt
    ) {
    }

    public record BtnConfigView(
            List<BtnConfigFieldView> fields,
            OffsetDateTime updatedAt
    ) {
    }

    public record UpdateBtnConfigRequest(
            Map<String, String> values
    ) {
    }
}
