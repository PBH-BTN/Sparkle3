package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.exception.AccessDeniedException;

import java.io.Serializable;

public class ClientAuthenticationCredential implements Serializable {
    private final String appId;
    private final String appSecret;

    public ClientAuthenticationCredential(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public String appId() {
        return appId.trim();
    }

    public String appSecret() {
        return appSecret.trim();
    }

    public boolean isValid() {
        return appId != null && appSecret != null;
    }

    public void verifyOrThrow() throws AccessDeniedException {
        if (!isValid()) {
            throw new AccessDeniedException("请求未鉴权，客户端实现必须进行登录鉴权：https://github.com/PBH-BTN/BTN-Spec");
        }
    }
}
