package com.ghostchu.btn.sparkle.autoconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.time.Duration;
import java.util.Arrays;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    static final CacheControl HASHED_ASSET_CACHE_CONTROL = CacheControl
            .maxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();
    private static final CacheControl SPA_DOCUMENT_CACHE_CONTROL = CacheControl
            .noCache()
            .mustRevalidate();

    private final String[] staticLocations;
    private final String[] assetLocations;

    public StaticResourceConfig(
            @Value("${spring.web.resources.static-locations:"
                    + "classpath:/static/,file:./static/,file:./data/static/}")
            String staticLocations
    ) {
        this.staticLocations = Arrays.stream(
                        StringUtils.commaDelimitedListToStringArray(staticLocations)
                )
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .map(StaticResourceConfig::withTrailingSlash)
                .toArray(String[]::new);
        this.assetLocations = Arrays.stream(this.staticLocations)
                .map(location -> location + "assets/")
                .toArray(String[]::new);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(assetLocations)
                .setCacheControl(HASHED_ASSET_CACHE_CONTROL);

        registry.addResourceHandler("/**")
                .addResourceLocations(staticLocations)
                .setCacheControl(SPA_DOCUMENT_CACHE_CONTROL)
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver());
    }

    private static String withTrailingSlash(String location) {
        return location.endsWith("/") ? location : location + "/";
    }

    static boolean shouldServeSpaDocument(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return true;
        }
        String path = "/" + resourcePath;
        return !(path.equals("/error")
                || path.equals("/favicon.ico")
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
                || path.startsWith("/websocket/"));
    }

    private static final class SpaFallbackResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws java.io.IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (!shouldServeSpaDocument(resourcePath)) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }
    }
}
