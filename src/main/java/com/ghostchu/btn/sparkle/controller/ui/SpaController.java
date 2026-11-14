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
            "/register",
            "/user",
            "/user/profile",
            "/user/points",
            "/user/submissions",
            "/user/inbox",
            "/user/profile/{uid}",
            "/user/privacy-mode",
            "/user/settings",
            "/user/settings/account",
            "/user/settings/personalization",
            "/user/settings/privacy",
            "/user/settings/notifications",
            "/user/settings/community-groups",
            "/user/settings/afdian",
            "/account-deletion",
            "/mail-unsubscribe",
            "/userapp",
            "/userapp/create",
            "/userapp/created",
            "/banhistory",
            "/ip-query",
            "/client-discovery",
            "/swarm-tracker",
            "/rules/preview",
            "/ranking/swarmStatistics",
            "/admin",
            "/admin/dashboard",
            "/admin/tasks",
            "/admin/users",
            "/admin/userapps",
            "/admin/rules",
            "/admin/modules",
            "/admin/points-modules",
            "/admin/settings",
            "/admin/announcements",
            "/admin/messages",
            "/admin/community-groups",
            "/admin/afdian",
            "/admin/github-sync",
            "/admin/btn",
            "/admin/navigation",
            "/admin/storage",
            "/admin/version-alerts",
            "/admin/user-contact-emails",
            "/admin/ai-moderation",
            "/admin/ip-query-comments",
            "/admin/database-config",
            "/oauth/error",
            "/ping/queryIp/widget"
    })
    public String index() {
        return "forward:/index.html";
    }
}
