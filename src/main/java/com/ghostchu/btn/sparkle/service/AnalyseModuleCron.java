package com.ghostchu.btn.sparkle.service;

import org.jetbrains.annotations.Nullable;

final class AnalyseModuleCron {
    static final String DISABLED = "-";

    private AnalyseModuleCron() {
    }

    static boolean isDisabled(@Nullable String expression) {
        return expression == null
                || expression.isBlank()
                || DISABLED.equals(expression.trim());
    }

    static String normalize(@Nullable String expression) {
        return isDisabled(expression) ? DISABLED : expression.trim();
    }
}
