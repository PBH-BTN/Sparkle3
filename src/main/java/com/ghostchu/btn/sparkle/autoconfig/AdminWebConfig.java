package com.ghostchu.btn.sparkle.autoconfig;

import com.ghostchu.btn.sparkle.security.AdminAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminWebConfig implements WebMvcConfigurer {
    private final AdminAccessInterceptor adminAccessInterceptor;

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(adminAccessInterceptor)
                .addPathPatterns("/admin", "/admin/**", "/api/admin/**");
    }
}
