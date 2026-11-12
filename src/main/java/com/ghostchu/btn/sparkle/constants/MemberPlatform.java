package com.ghostchu.btn.sparkle.constants;

import java.util.Arrays;
import java.util.Optional;

public enum MemberPlatform {
    AFDIAN,
    PATREON,
    KOFI,
    MIANBAODUO,
    LIANDONG;

    public static Optional<MemberPlatform> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(platform -> platform.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
