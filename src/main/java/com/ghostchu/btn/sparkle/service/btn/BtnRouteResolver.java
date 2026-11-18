package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Component
@RequiredArgsConstructor
public class BtnRouteResolver {
    private final BtnRuntimeConfigService config;
    private final IPDBManager ipdb;

    public String resolve(String remoteAddress) {
        if (!config.booleanValue("rootUrls", "autoRouteEnabled")) {
            return config.text("rootUrls", "rootUrl");
        }
        var lazyGeoData = ipdb.queryIPDB(InetAddress.ofLiteral(remoteAddress)).geoData();
        var geoData = lazyGeoData == null ? null : lazyGeoData.get();
        return config.routeUrl(
                geoData == null || geoData.getNetwork() == null ? null : geoData.getNetwork().getIsp(),
                geoData == null || geoData.getCity() == null ? null : geoData.getCity().getCnProvince()
        );
    }
}
