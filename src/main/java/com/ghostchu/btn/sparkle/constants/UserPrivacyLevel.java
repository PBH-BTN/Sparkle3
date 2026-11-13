package com.ghostchu.btn.sparkle.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserPrivacyLevel {
    DISABLED(0, false, true, false, true, true),
    LOW(1, true, false, true, false, true),
    MEDIUM(2, true, false, true, false, true),
    HIGH(3, true, false, false, false, false);

    @EnumValue
    private final int level;
    private final boolean allowBlurredUsername;
    private final boolean allowFullUserName;
    private final boolean allowBlurredAvatar;
    private final boolean allowOriginalAvatar;
    private final boolean allowContactEmail;

    public boolean isAllowPublicPoints() {
        return level <= LOW.level;
    }

}
