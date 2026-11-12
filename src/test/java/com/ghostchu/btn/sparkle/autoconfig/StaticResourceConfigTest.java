package com.ghostchu.btn.sparkle.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceConfigTest {
    @Test
    void hashedAssetsUseLongLivedImmutableCaching() {
        String header = StaticResourceConfig.HASHED_ASSET_CACHE_CONTROL.getHeaderValue();

        assertTrue(header.contains("max-age=31536000"));
        assertTrue(header.contains("public"));
        assertTrue(header.contains("immutable"));
    }

    @Test
    void assetResourceHandlerReturnsImmutableCacheHeader() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebTestConfig.class);
            context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get("/assets/cache-test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            "Cache-Control",
                            allOf(
                                    containsString("max-age=31536000"),
                                    containsString("public"),
                                    containsString("immutable")
                            )
                    ));
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(StaticResourceConfig.class)
    static class WebTestConfig {
    }
}
