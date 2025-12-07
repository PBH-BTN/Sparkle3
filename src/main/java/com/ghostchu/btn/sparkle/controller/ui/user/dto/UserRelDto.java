package com.ghostchu.btn.sparkle.controller.ui.user.dto;

import com.ghostchu.btn.sparkle.entity.UserRel;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UserRelDto {
    private final String platformUserId;
    private final String platform;
    private final String platformUserEmail;
    private final String platformUserLogin;

    public UserRelDto(UserRel rel){
        this.platform = rel.getPlatform();
        this.platformUserId = rel.getPlatformUserId();
        this.platformUserEmail = rel.getPlatformUserEmail();
        this.platformUserLogin = rel.getPlatformUserLogin();
    }
}
