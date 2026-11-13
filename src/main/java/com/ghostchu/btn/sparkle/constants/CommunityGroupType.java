package com.ghostchu.btn.sparkle.constants;

import java.util.Arrays;
import java.util.Optional;

public enum CommunityGroupType {
    GITHUB,
    LINK;

    public static Optional<CommunityGroupType> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
