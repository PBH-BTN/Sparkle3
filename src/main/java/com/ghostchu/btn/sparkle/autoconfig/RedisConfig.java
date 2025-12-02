package com.ghostchu.btn.sparkle.autoconfig;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean("stringStringRedisTemplate")
    public RedisTemplate<String, String> stringStringRedisTemplate(@NotNull RedisConnectionFactory factory) {
        RedisSerializer<@NotNull String> redisSerializer = new StringRedisSerializer();
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setKeySerializer(redisSerializer);
        template.setHashKeySerializer(redisSerializer);
        template.setValueSerializer(redisSerializer);
        template.setHashValueSerializer(redisSerializer);
        template.setConnectionFactory(factory);
        return template;
    }

    @Bean("stringLongRedisTemplate")
    public RedisTemplate<String, Long> stringLongRedisTemplate(@NotNull RedisConnectionFactory factory) {
        RedisSerializer<@NotNull String> stringSerializer = new StringRedisSerializer();
        RedisSerializer<@NotNull Long> jsonSerializer = new JacksonJsonRedisSerializer<>(Long.class);
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        return template;
    }
}
