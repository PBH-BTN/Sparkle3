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
    void servesTheVueDocumentForExplicitApplicationRoutes() throws Exception {
        for (String path : new String[]{
                "/",
                "/user/profile/42",
                "/user/submissions",
                "/ip-query",
                "/admin/ai-moderation",
                "/ping/queryIp/widget"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }
}
