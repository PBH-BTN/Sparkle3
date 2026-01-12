package com.ghostchu.btn.sparkle.controller.ui.user.dto;

import com.ghostchu.btn.sparkle.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/**
 * UserDto 可能会暴露给外部接口，禁止包含敏感数据
 */
public class UserDto implements Serializable {
    private Long id;
    private String avatar;
    private String nickname;
    private String email;
    private OffsetDateTime registerAt;
    private OffsetDateTime bannedAt;
    private String bannedReason;

    public UserDto(User user) {
        this.id = user.getId();
        this.avatar = user.getAvatar();
        this.nickname = user.getNickname();
        this.email = user.getEmail();
        this.registerAt = user.getRegisterAt();
        this.bannedAt = user.getBannedAt();
        this.bannedReason = user.getBannedReason();
    }
}
