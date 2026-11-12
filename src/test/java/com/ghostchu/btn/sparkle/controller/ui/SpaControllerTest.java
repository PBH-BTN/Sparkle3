package com.ghostchu.btn.sparkle.controller.ui;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaControllerTest {
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SpaController())
            .build();

    @Test
    void servesTheVueDocumentForEveryAdminRoute() throws Exception {
        for (String path : new String[]{
                "/admin",
                "/admin/btn",
                "/admin/github-sync",
                "/admin/future-admin-page",
                "/future-public-page",
                "/ping/queryIp/widget"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void doesNotTurnMissingApisOrStaticResourcesIntoTheVueDocument() throws Exception {
        for (String path : new String[]{
                "/api/admin/missing",
                "/assets/missing.js",
                "/ping/missing",
                "/ping/queryIp"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
    }
}
