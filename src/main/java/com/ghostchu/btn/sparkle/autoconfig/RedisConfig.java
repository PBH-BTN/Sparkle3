package com.ghostchu.btn.sparkle.autoconfig;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {

    @Bean("genericStringStringTemplate")
    public RedisTemplate<String, String> redisTemplateGenericStringString(@NotNull RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }

    @Bean("torrentIdRedisTemplate")
    public RedisTemplate<String, Long> redisTemplateTorrentId(@NotNull RedisConnectionFactory factory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }
}
