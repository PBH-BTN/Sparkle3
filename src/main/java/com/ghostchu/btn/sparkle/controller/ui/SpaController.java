package com.ghostchu.btn.sparkle.controller.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vue application for browser routes. Vue Router owns navigation
 * after the initial document has loaded.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/user",
            "/user/profile",
            "/user/privacy-mode",
            "/userapp",
            "/userapp/create",
            "/userapp/created",
            "/banhistory",
            "/client-discovery",
            "/swarm-tracker",
            "/tracker",
            "/ranking/swarmStatistics",
            "/admin",
            "/admin/dashboard",
            "/admin/users",
            "/admin/userapps",
            "/admin/rules",
            "/admin/settings",
            "/admin/storage",
            "/admin/version-alerts",
            "/admin/database-config",
            "/oauth/error",
            "/ping/queryIp/widget"
    })
    public String index() {
        return "forward:/index.html";
    }
}
