package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.exception.AccessDeniedException;

import java.io.Serializable;

public class ClientAuthenticationCredential implements Serializable {
    private final String appId;
    private final String appSecret;
    private final String installationId;

    public ClientAuthenticationCredential(String appId, String appSecret, String installationId) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.installationId = installationId;
    }

    public String appId() {
        return appId.trim();
    }

    public String appSecret() {
        return appSecret.trim();
    }

    public String installationId() {
        return installationId.trim();
    }

    public boolean isValid() {
        return (appId != null && appSecret != null) || (installationId != null && !installationId.trim().isEmpty());
    }

    public void verifyOrThrow() throws AccessDeniedException {
        if (!isValid()) {
            throw new AccessDeniedException("请求未鉴权，客户端实现必须进行登录鉴权：https://github.com/PBH-BTN/BTN-Spec");
        }
    }
}
