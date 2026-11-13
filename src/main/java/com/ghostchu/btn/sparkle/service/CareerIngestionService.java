package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.CareerIngestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CareerIngestionService {
    private final CareerIngestionMapper mapper;

    private String key(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String,Object> event(long uid, String kind, String identity, OffsetDateTime at) {
        Map<String,Object> p = new HashMap<>();
        p.put("userId", uid);
        p.put("kind", kind);
        p.put("key", key(kind + ":" + identity));
        p.put("moment", at);
        return p;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void swarm(long uid, SwarmTracker row) {
        var p = event(uid, "swarm", row.getUserappsId() + ":" + row.getUserDownloader()
                + ":" + row.getTorrentId() + ":" + row.getPeerIp().getHostAddress()
                + ":" + row.getPeerPort() + ":" + row.getFirstTimeSeen().toInstant(), row.getLastTimeSeen());
        long upload = Math.max(0, row.getToPeerTraffic());
        long download = Math.max(0, row.getFromPeerTraffic());
        p.put("upload", upload);
        p.put("download", download);
        // First observation establishes a baseline; only the reported latest delta is counted.
        p.put("baselineUpload", Math.max(0, upload - Math.max(0, row.getToPeerTrafficOffset())));
        p.put("baselineDownload", Math.max(0, download - Math.max(0, row.getFromPeerTrafficOffset())));
        mapper.init(p);
        var previous = mapper.checkpoint(p);
        p.put("uploadDelta", Math.max(0, upload - previous.get("uploaded")));
        p.put("downloadDelta", Math.max(0, download - previous.get("downloaded")));
        p.put("started", row.getLastTimeSeen().minusMinutes(5));
        p.put("ip", row.getPeerIp().getHostAddress());
        mapper.advance(p);
        mapper.traffic(p);
        mapper.event(p);
        p.put("observationKey", key(p.get("key") + ":" + row.getLastTimeSeen().toInstant()));
        mapper.observations(p);
        p.put("torrentId", row.getTorrentId());
        p.put("seeding", row.getUserProgress() >= 1);
        mapper.activity(p);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ban(long uid, BanHistory row) {
        var p = event(uid, "ban", row.getUserappsId() + ":" + row.getTorrentId()
                + ":" + row.getPeerIp().getHostAddress() + ":" + row.getPeerPort()
                + ":" + row.getPopulateTime().toInstant() + ":" + row.getModuleName()
                + ":" + row.getRule(), row.getPopulateTime());
        mapper.event(p);
        p.put("ip", row.getPeerIp().getHostAddress());
        p.put("started", row.getPopulateTime());
        p.put("observationKey", p.get("key"));
        mapper.observations(p);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void featureByApp(long appId, long hash, OffsetDateTime at) {
        var p = event(0, "feature", Long.toString(hash), at);
        p.put("appId",appId);
        mapper.feature(p);
    }
}
