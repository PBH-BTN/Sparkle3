package com.ghostchu.btn.sparkle.exception;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

public class UserApplicationBannedException extends BusinessException {
    public UserApplicationBannedException(@NotNull String reason) {
        super(HttpStatus.FORBIDDEN, reason);
    }
}
