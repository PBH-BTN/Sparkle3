package com.ghostchu.btn.sparkle.controller.ui.userapp.dto;

import com.ghostchu.btn.sparkle.controller.ui.user.dto.UserDto;
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
public class UserApplicationVerboseDto implements Serializable {
    private Long id;
    private String appId;
    private String appSecret;
    private String comment;
    private OffsetDateTime createdAt;
    private UserDto user;
}
