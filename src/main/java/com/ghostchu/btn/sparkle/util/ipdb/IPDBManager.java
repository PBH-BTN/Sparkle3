package com.ghostchu.btn.sparkle.util.ipdb;

import com.ghostchu.btn.sparkle.util.LazyLoad;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class IPDBManager {
    private final Cache<String, IPDBResponse> geoIpCache = CacheBuilder.newBuilder()
            .expireAfterAccess(300000, TimeUnit.MILLISECONDS)
            .maximumSize(10_000)
            .softValues()
            .build();
    @Getter
    @Nullable
    private IPDB ipdb = null;

    public IPDBManager(com.ghostchu.btn.sparkle.service.tasks.TaskStatusService tasks) {
        tasks.ensure(com.ghostchu.btn.sparkle.service.tasks.TaskDefinition.startup(
                "geoip-database-startup", com.ghostchu.btn.sparkle.service.tasks.TaskGroup.STARTUP,
                "admin.tasks.items.geoip-database-startup.name",
                "admin.tasks.items.geoip-database-startup.description"));
        var execution = tasks.begin("geoip-database-startup", "STARTUP");
        try {
            setupIPDB();
            execution.success();
        } catch (Exception exception) {
            execution.failure(exception);
            log.error("Unable to setup IPDB", exception);
        }
    }

    private void setupIPDB() throws java.io.IOException {
        String databaseCity = "GeoLite2-City";
        String databaseASN = "GeoLite2-ASN";
        String databaseGeoCN = "GeoCN";
        this.ipdb = new IPDB(new File("data/geoip"), "", "",
                databaseCity, databaseASN, databaseGeoCN, true, "Sparkle/2.1");
    }

    public IPDBResponse queryIPDB(InetAddress address) {
        try {
            return geoIpCache.get(address.getHostAddress(), () -> {
                if (ipdb == null) {
                    return new IPDBResponse(new LazyLoad<>(() -> null));
                } else {
                    return new IPDBResponse(new LazyLoad<>(() -> {
                        try {
                            return ipdb.query(address);
                        } catch (Exception e) {
                            Sentry.captureException(e);
                            return null;
                        }
                    }));
                }
            });
        } catch (ExecutionException e) {
            Sentry.captureException(e);
            return new IPDBResponse(null);
        }
    }

    public void close() {
        if (ipdb != null) {
            ipdb.close();
        }
    }

    public record IPDBResponse(LazyLoad<IPGeoData> geoData) {
    }
}
