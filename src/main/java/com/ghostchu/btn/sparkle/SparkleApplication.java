package com.ghostchu.btn.sparkle;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@MapperScan("com.ghostchu.btn.sparkle.mapper")
//@EnableAsync
@EnableScheduling
//@EnableRetry
@EnableCaching
@EnableRedisHttpSession
public class SparkleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SparkleApplication.class, args);
    }

}
