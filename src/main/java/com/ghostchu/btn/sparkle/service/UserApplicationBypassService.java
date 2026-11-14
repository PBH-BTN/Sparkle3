package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserApplicationBypassService {
    private final IUserappsHeartbeatService heartbeats;
    private final IPDBManager ipdb;

    public List<BypassIp> list(Userapp application) {
        if (!Boolean.TRUE.equals(application.getBtnBypass()) || application.getBannedAt() != null) return List.of();
        var end = OffsetDateTime.now();
        var result = new LinkedHashMap<String, BypassIp>();
        for (var heartbeat : heartbeats.fetchHeartBeatsByUserAppIdInTimeRange(application.getId(),end.minusHours(1),end)) {
            var address = heartbeat.getIp();
            if (address == null || result.containsKey(address.getHostAddress())) continue;
            var geo = ipdb.queryIPDB(address);
            result.put(address.getHostAddress(),new BypassIp(address.getHostAddress(),
                    geo == null || geo.geoData() == null ? null : geo.geoData().get()));
        }
        return List.copyOf(result.values());
    }

    public record BypassIp(String ip, IPGeoData peerGeoip) { }
}
