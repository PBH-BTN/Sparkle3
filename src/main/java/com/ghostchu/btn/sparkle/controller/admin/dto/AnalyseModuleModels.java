package com.ghostchu.btn.sparkle.controller.admin.dto;

import java.util.List;
import java.util.Map;

public final class AnalyseModuleModels {
    private AnalyseModuleModels() {
    }

    public record ModuleFieldView(
            String field,
            String key,
            String type,
            String value,
            boolean restartRequired
    ) {
    }

    public record ModuleView(
            String id,
            boolean enabled,
            List<ModuleFieldView> fields
    ) {
    }

    public record UpdateModuleRequest(
            Map<String, String> values
    ) {
    }

    public record RunModuleResponse(
            String module,
            boolean started
    ) {
    }
}
