package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class PowCaptchaFailureException extends BusinessException {
    public PowCaptchaFailureException() {
        super(HttpStatus.FORBIDDEN, "Proof Of Work Captcha 失败，计算结果无效或者挑战已过期");
    }
}
