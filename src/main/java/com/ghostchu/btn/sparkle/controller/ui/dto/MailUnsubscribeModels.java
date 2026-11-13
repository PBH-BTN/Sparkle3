package com.ghostchu.btn.sparkle.controller.ui.dto;

import java.util.List;

public final class MailUnsubscribeModels {
    private MailUnsubscribeModels() { }

    public record Option(String category, boolean emailEnabled) { }

    public record Update(List<String> categories) { }
}
