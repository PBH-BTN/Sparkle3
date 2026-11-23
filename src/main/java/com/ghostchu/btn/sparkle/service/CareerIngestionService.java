package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.CareerBatchMapper;
import com.ghostchu.btn.sparkle.mapper.CareerBatchMapper.DailyTrafficDelta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CareerIngestionService {
    private static final int BATCH_SIZE = 500;

    private final CareerBatchMapper batchMapper;
    private final com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService runtimeConfig;
    private final CareerRedisCache cache;

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

    private String swarmSessionKey(SwarmTracker row) {
        return key("swarm:" + row.getUserappsId() + ":" + row.getUserDownloader() + ":" + row.getTorrentId()
                + ":" + row.getPeerIp().getHostAddress() + ":" + row.getPeerPort() + ":" + row.getFirstTimeSeen().toInstant());
    }

    public PreparedSwarm prepareSwarm(long uid, List<SwarmTracker> rows) {
        // Torrent-time rows are shared by all applications of a user. Keep their
        // order stable across the entire report, not just inside each SQL batch.
        rows = rows.stream().sorted(Comparator.comparingLong(SwarmTracker::getTorrentId)
                .thenComparing(this::swarmSessionKey)).toList();
        long activityWindow = runtimeConfig.reportingWindowMillis("syncSwarm");
        long heartbeatWindow = runtimeConfig.reportingWindowMillis("heartbeat");
        var batches = new ArrayList<SwarmBatch>();
        for (int offset=0; offset<rows.size(); offset+=BATCH_SIZE) {
            var values = new java.util.ArrayList<Map<String,Object>>();
            var identities = new java.util.ArrayList<String>();
            for (var row : rows.subList(offset,Math.min(offset+BATCH_SIZE,rows.size()))) {
                var p = event(uid,"swarm",row.getUserappsId()+":"+row.getUserDownloader()+":"+row.getTorrentId()+":"+row.getPeerIp().getHostAddress()+":"+row.getPeerPort()+":"+row.getFirstTimeSeen().toInstant(),row.getLastTimeSeen());
                long upload=Math.max(0,row.getToPeerTraffic()), download=Math.max(0,row.getFromPeerTraffic());
                p.put("upload",upload); p.put("download",download);
                p.put("initialUpload",Math.min(upload,Math.max(0,row.getToPeerTrafficOffset())));
                p.put("initialDownload",Math.min(download,Math.max(0,row.getFromPeerTrafficOffset())));
                p.put("at",row.getLastTimeSeen()); p.put("ip",row.getPeerIp().getHostAddress());
                p.put("torrentId",row.getTorrentId()); p.put("seeding",row.getUserProgress()>=1);
                p.put("observationKey",key(p.get("key")+":"+row.getLastTimeSeen().toInstant()));
                identities.add(p.get("key")+":"+row.getLastTimeSeen().toInstant()+":"+upload+":"+download+":"+row.getUserProgress()+":"+p.get("initialUpload")+":"+p.get("initialDownload"));
                values.add(p);
            }
            java.util.Collections.sort(identities);
            String delivery=key(uid+":"+activityWindow+":"+heartbeatWindow+":"+String.join(";",identities));
            if (cache.alreadyCommitted(delivery)) continue;
            batches.add(new SwarmBatch(values, delivery));
        }
        return new PreparedSwarm(uid, activityWindow, heartbeatWindow, batches);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void swarmBatch(PreparedSwarm report) {
        var dailyTraffic = new ArrayList<DailyTrafficDelta>();
        for (var batch : report.batches) {
            dailyTraffic.addAll(batchMapper.swarm(report.userId, batch.rows(),
                    report.activityWindow, report.heartbeatWindow));
        }
        dailyTraffic.sort(Comparator.comparingLong(DailyTrafficDelta::userId));
        for (int offset = 0; offset < dailyTraffic.size(); offset += BATCH_SIZE) {
            batchMapper.upsertLifetimeTraffic(dailyTraffic.subList(offset, Math.min(offset + BATCH_SIZE, dailyTraffic.size())));
        }
        writeDailyTraffic(dailyTraffic);
    }

    public void markSwarmCommitted(PreparedSwarm report) {
        for (var batch : report.batches) {
            cache.markCommitted(batch.fingerprint());
        }
    }

    private record SwarmBatch(List<Map<String, Object>> rows, String fingerprint) {}

    public static final class PreparedSwarm {
        private final long userId;
        private final long activityWindow;
        private final long heartbeatWindow;
        private final List<SwarmBatch> batches;

        private PreparedSwarm(long userId, long activityWindow, long heartbeatWindow, List<SwarmBatch> batches) {
            this.userId = userId;
            this.activityWindow = activityWindow;
            this.heartbeatWindow = heartbeatWindow;
            this.batches = List.copyOf(batches);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bans(long uid, List<BanHistory> rows, long heartbeatWindow) {
        var values = new java.util.ArrayList<Map<String,Object>>();
        for (var row : rows) {
            var p=event(uid,"ban",row.getUserappsId()+":"+row.getTorrentId()+":"+row.getPeerIp().getHostAddress()+":"+row.getPeerPort()+":"+row.getPopulateTime().toInstant()+":"+row.getModuleName()+":"+row.getRule(),row.getPopulateTime());
            p.put("ip",row.getPeerIp().getHostAddress());
            p.put("upload",row.getToPeerTraffic() == null ? 0 : Math.max(0,row.getToPeerTraffic()));
            p.put("download",row.getFromPeerTraffic() == null ? 0 : Math.max(0,row.getFromPeerTraffic()));
            values.add(p);
        }
        submit(uid, values, false, heartbeatWindow);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void featuresByApp(long appId,java.util.List<com.ghostchu.btn.sparkle.entity.ClientDiscovery> rows) {
        submit(appId, rows.stream().map(row -> event(0, "feature", row.getHash().toString(), row.getFoundAt()))
                .toList(), true, 0);
    }

    private void submit(long uid, List<Map<String, Object>> rows, boolean feature, long heartbeatWindow) {
        rows = rows.stream().sorted(Comparator.comparing(row -> row.get("key").toString())).toList();
        var dailyTraffic = new ArrayList<DailyTrafficDelta>();
        var featureDaily = new ArrayList<CareerBatchMapper.CareerDailyDelta>();
        for (int offset=0;offset<rows.size();offset+=BATCH_SIZE) {
            var chunk=rows.subList(offset,Math.min(offset+BATCH_SIZE,rows.size()));
            if (feature) {
                featureDaily.addAll(batchMapper.features(uid,chunk));
            } else {
                dailyTraffic.addAll(batchMapper.bans(uid,chunk,heartbeatWindow));
            }
        }
        writeCareerDaily(featureDaily);
        writeDailyTraffic(dailyTraffic);
    }

    void writeDailyTraffic(List<DailyTrafficDelta> rows) {
        var career = new ArrayList<CareerBatchMapper.CareerDailyDelta>();
        for (var row : rows) {
            if (row.swarmEvents() > 0) career.add(new CareerBatchMapper.CareerDailyDelta(row.userId(), row.day(), "swarm", row.swarmEvents()));
            if (row.banEvents() > 0) career.add(new CareerBatchMapper.CareerDailyDelta(row.userId(), row.day(), "ban", row.banEvents()));
            if (row.observationEvents() > 0) career.add(new CareerBatchMapper.CareerDailyDelta(row.userId(), row.day(), "observation", row.observationEvents()));
        }
        writeCareerDaily(career);
        // Row locks survive each batch. Sort the entire report before splitting the upserts.
        rows.sort(Comparator.comparingLong(DailyTrafficDelta::userId).thenComparing(DailyTrafficDelta::day));
        for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
            batchMapper.upsertDailyTraffic(rows.subList(offset, Math.min(offset + BATCH_SIZE, rows.size())));
        }
    }

    private void writeCareerDaily(List<CareerBatchMapper.CareerDailyDelta> career) {
        career.sort(Comparator.comparingLong(CareerBatchMapper.CareerDailyDelta::userId)
                .thenComparing(CareerBatchMapper.CareerDailyDelta::kind)
                .thenComparing(CareerBatchMapper.CareerDailyDelta::day));
        for (int offset = 0; offset < career.size(); offset += BATCH_SIZE) {
            batchMapper.upsertCareerDaily(career.subList(offset, Math.min(offset + BATCH_SIZE, career.size())));
        }
    }

}
