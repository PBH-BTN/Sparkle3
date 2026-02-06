package com.ghostchu.btn.sparkle;

import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.zaxxer.hikari.pool.HikariPool;
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

import javax.crypto.BadPaddingException;
import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@SpringBootApplication
@MapperScan("com.ghostchu.btn.sparkle.mapper")
//@EnableAsync
@EnableScheduling
//@EnableRetry
@EnableCaching
@EnableRedisHttpSession
public class SparkleApplication {

    public static void main(String[] args) {
        Sentry.init(sentryOptions -> {
            sentryOptions.setDsn("https://72052bef98414ea29bc78b5333c2a527@glitchtip.pbh-btn.com/3");
            sentryOptions.setEnableExternalConfiguration(true); // Read DSN from sentry.properties
            sentryOptions.setAttachThreads(true);
            sentryOptions.setPrintUncaughtStackTrace(true);
            sentryOptions.setEnableUncaughtExceptionHandler(true);
            sentryOptions.setProfilesSampleRate(1.0d);
            sentryOptions.setTag("os", System.getProperty("os.name"));
            sentryOptions.setTag("osarch", System.getProperty("os.arch"));
            sentryOptions.setTag("osversion", System.getProperty("os.version"));
        });
        SpringApplication.run(SparkleApplication.class, args);
    }

}
