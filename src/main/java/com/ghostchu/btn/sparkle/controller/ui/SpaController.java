package com.ghostchu.btn.sparkle.controller.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the Vue application for browser routes. Vue Router owns navigation
 * after the initial document has loaded.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/user/profile/{uid}",
            "/ping/queryIp/widget"
    })
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/{*path}")
    public String fallback(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (shouldBypassSpaFallback(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }

    static boolean shouldBypassSpaFallback(String path) {
        return path.equals("/error")
                || path.equals("/favicon.ico")
                || path.equals("/index.html")
                || path.equals("/api")
                || path.startsWith("/api/")
                || path.equals("/assets")
                || path.startsWith("/assets/")
                || path.equals("/css")
                || path.startsWith("/css/")
                || path.equals("/js")
                || path.startsWith("/js/")
                || path.equals("/img")
                || path.startsWith("/img/")
                || path.equals("/webjars")
                || path.startsWith("/webjars/")
                || path.equals("/actuator")
                || path.startsWith("/actuator/")
                || path.equals("/oauth2")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/")
                || path.equals("/ping")
                || path.startsWith("/ping/")
                || path.equals("/proxy")
                || path.startsWith("/proxy/")
                || path.equals("/announce")
                || path.equals("/tracker")
                || path.startsWith("/tracker/announce")
                || path.equals("/ws")
                || path.startsWith("/ws/")
                || path.equals("/websocket")
                || path.startsWith("/websocket/");
    }
}
