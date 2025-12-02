package com.ghostchu.btn.sparkle.exception;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

public class UserBannedException extends BusinessException {

    public UserBannedException(@NotNull String reason) {
        super(HttpStatus.FORBIDDEN, reason);
    }
}
