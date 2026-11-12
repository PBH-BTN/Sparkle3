package com.ghostchu.btn.sparkle.autoconfig;

import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.util.GlobalpingApiClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalPingConfig {
    @Bean
    @NotNull
    public GlobalpingApiClient getClient(BtnRuntimeConfigService configService) {
        String globalpingToken = configService.text("measure", "globalpingToken");
        var client = new GlobalpingApiClient(null);
        if (!globalpingToken.isBlank()) {
            client.setAccessToken(globalpingToken);
        }
        return client;
    }
}
