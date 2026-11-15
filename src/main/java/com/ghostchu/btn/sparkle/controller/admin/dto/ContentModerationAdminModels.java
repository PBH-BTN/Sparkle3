package com.ghostchu.btn.sparkle.controller.admin.dto;

public final class ContentModerationAdminModels {
    private ContentModerationAdminModels() {}

    public record DictionaryUpdate(String version, Boolean enabled, String words) {}
}
