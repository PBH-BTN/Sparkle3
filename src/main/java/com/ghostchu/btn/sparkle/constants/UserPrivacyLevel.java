package com.ghostchu.btn.sparkle.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserPrivacyLevel {
    DISABLED(0, true, true, true, true),
    LOW(1, true, false, true, false),
    MEDIUM(2, true, false,false,false),
    HIGH(3, false, false, false, false);

    @EnumValue
    private final int level;
    private final boolean allowBlurredUsername;
    private final boolean allowFullUserName;
    private final boolean allowBlurredAvatar;
    private final boolean allowOriginalAvatar;

}
