package com.ghostchu.btn.sparkle.autoconfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    static final CacheControl HASHED_ASSET_CACHE_CONTROL = CacheControl
            .maxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(
                        "classpath:/static/assets/",
                        "file:./static/assets/"
                )
                .setCacheControl(HASHED_ASSET_CACHE_CONTROL);
    }
}
