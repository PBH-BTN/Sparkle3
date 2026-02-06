package com.ghostchu.btn.sparkle;

import io.sentry.Sentry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
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
        Sentry.init();
        Sentry.init("https://72052bef98414ea29bc78b5333c2a527@glitchtip.pbh-btn.com/3");
        SpringApplication.run(SparkleApplication.class, args);
    }

}
