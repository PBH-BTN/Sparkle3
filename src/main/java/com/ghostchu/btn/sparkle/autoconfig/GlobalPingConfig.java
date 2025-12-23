package com.ghostchu.btn.sparkle.autoconfig;

import com.ghostchu.btn.sparkle.util.GlobalpingApiClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GlobalPingConfig {
    @Bean
    @NotNull
    public GlobalpingApiClient getClient(@Value("${sparkle.measure.globalping-token}") String globalpingToken) {
        var client = new GlobalpingApiClient(null);
        if (globalpingToken != null) {
            client.setAccessToken(globalpingToken);
        }
        return client;
    }
}
