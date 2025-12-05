package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.security.ClientAuthenticationCredential;
import com.ghostchu.btn.sparkle.service.IPowCaptchaService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.util.HexUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Component
public class BasePingController {
    @Autowired
    protected HttpServletRequest request;
    @Autowired
    private IUserappService userappService;
    @Autowired
    private IPowCaptchaService pingCaptchaService;

    public boolean validatePowCaptcha() {
        String powId = request.getHeader("X-BTN-PowID");
        String powSolution = request.getHeader("X-BTN-PowSolution");
        if (powId == null || powSolution == null) {
            return false;
        }
        return pingCaptchaService.validateSession(powId, powSolution);
    }

    public boolean isAcceptablePublicIp(@NotNull String ip) {
        try {
            InetAddress inetAddress = InetAddress.ofLiteral(ip);
            // we only accept internet public IPs
            return !inetAddress.isAnyLocalAddress() && !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && !inetAddress.isSiteLocalAddress() && !inetAddress.isMulticastAddress();
        } catch (Exception e) {
            return false;
        }
    }

    @NotNull
    public Userapp verifyUserApplication() throws UserApplicationNotFoundException, UserApplicationBannedException, AccessDeniedException {
        var cred = cred(request);
        cred.verifyOrThrow();
        var userApp = userappService.loginViaCredential(cred.appId(), cred.appSecret());
        if (userApp == null) {
            throw new UserApplicationNotFoundException();
        }
        if (userApp.getBannedAt() != null) {
            throw new UserApplicationBannedException(userApp.getBannedReason());
        }
        return userApp;
    }

    @Nullable
    public Userapp verifyUserApplicationFailSafe() throws AccessDeniedException {
        var cred = cred(request);
        cred.verifyOrThrow();
        return userappService.loginViaCredential(cred.appId(), cred.appSecret());
    }

    public String cutPeerId(String in) {
        return HexUtil.cutPeerId(in);
    }

    public String sanitizeU0(String in) {
        return HexUtil.sanitizeU0(in);
    }

    @NotNull
    public ClientAuthenticationCredential cred(@NotNull HttpServletRequest request) {
        ClientAuthenticationCredential cred = readModernFromAuthentication(request);
        if (cred.isValid()) {
            return cred;
        }
        cred = readOldModernFromAuthentication(request); // 显然，BUG 变成了特性
        if (cred.isValid()) {
            return cred;
        }
        cred = readModernFromHeader(request);
        if (cred.isValid()) {
            return cred;
        }
        cred = readQueryFromUri(request);
        if (cred.isValid()) {
            return cred;
        }
        cred = readLegacy(request);
        return cred;
    }

    @NotNull
    private ClientAuthenticationCredential readQueryFromUri(@NotNull HttpServletRequest request) {
        String appId = request.getParameter("appId");
        String appSecret = request.getParameter("appSecret");
        String hardwareId = request.getParameter("hardwareId");
        String installationId = request.getParameter("installationId");
        return new ClientAuthenticationCredential(appId, appSecret);
    }

    @NotNull
    private ClientAuthenticationCredential readOldModernFromAuthentication(@NotNull HttpServletRequest request) {
        String header = request.getHeader("Authentication");
        if (header == null) {
            return new ClientAuthenticationCredential(null, null);
        }
        header = header.substring(7);
        String[] parser = header.split("@", 2);
        if (parser.length == 2) {
            return new ClientAuthenticationCredential(parser[0], parser[1]);
        }
        return new ClientAuthenticationCredential(null, null);
    }

    @NotNull
    private ClientAuthenticationCredential readModernFromAuthentication(@NotNull HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return new ClientAuthenticationCredential(null, null);
        }
        header = header.substring(7);
        String[] parser = header.split("@", 2);
        if (parser.length == 2) {
            return new ClientAuthenticationCredential(parser[0], parser[1]);
        }
        return new ClientAuthenticationCredential(null, null);
    }

    @NotNull
    private ClientAuthenticationCredential readModernFromHeader(@NotNull HttpServletRequest request) {
        String appId = request.getHeader("X-BTN-AppID");
        String appSecret = request.getHeader("X-BTN-AppSecret");
        return new ClientAuthenticationCredential(appId, appSecret);
    }

    @NotNull
    private ClientAuthenticationCredential readLegacy(@NotNull HttpServletRequest request) {
        String appId = request.getHeader("BTN-AppID");
        String appSecret = request.getHeader("BTN-AppSecret");
        return new ClientAuthenticationCredential(appId, appSecret);
    }
}
