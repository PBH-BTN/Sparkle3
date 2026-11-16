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

    @org.springframework.beans.factory.annotation.Autowired
    private CareerRedisCache cache;
    private final CareerBatchMapper batchMapper;
    private final com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService runtimeConfig;

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
    public void swarmBatch(long uid, java.util.List<SwarmTracker> rows) {
        long activityWindow = runtimeConfig.reportingWindowMillis("syncSwarm");
        long heartbeatWindow = runtimeConfig.reportingWindowMillis("heartbeat");
        var dailyTraffic = new ArrayList<DailyTrafficDelta>();
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
            if (cache != null && cache.alreadyCommitted(delivery)) continue;
            dailyTraffic.addAll(batchMapper.swarm(uid,values,activityWindow,heartbeatWindow));
            if (cache != null) cache.markAfterCommit(delivery);
        }
        writeDailyTraffic(dailyTraffic);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void swarm(long uid, SwarmTracker row) {
        swarmBatch(uid,java.util.List.of(row));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bans(long uid,java.util.List<BanHistory> rows) {
        var values = new java.util.ArrayList<Map<String,Object>>();
        for (var row : rows) {
            var p=event(uid,"ban",row.getUserappsId()+":"+row.getTorrentId()+":"+row.getPeerIp().getHostAddress()+":"+row.getPeerPort()+":"+row.getPopulateTime().toInstant()+":"+row.getModuleName()+":"+row.getRule(),row.getPopulateTime());
            p.put("ip",row.getPeerIp().getHostAddress());
            p.put("upload",row.getToPeerTraffic() == null ? 0 : Math.max(0,row.getToPeerTraffic()));
            p.put("download",row.getFromPeerTraffic() == null ? 0 : Math.max(0,row.getFromPeerTraffic()));
            values.add(p);
        }
        submit(uid,values,false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ban(long uid,BanHistory row) { bans(uid,java.util.List.of(row)); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void featuresByApp(long appId,java.util.List<com.ghostchu.btn.sparkle.entity.ClientDiscovery> rows) {
        submit(appId,rows.stream().map(row -> event(0,"feature",row.getHash().toString(),row.getFoundAt())).toList(),true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void featureByApp(long appId,long hash,OffsetDateTime at) {
        submit(appId,java.util.List.of(event(0,"feature",Long.toString(hash),at)),true);
    }

    private void submit(long uid,java.util.List<Map<String,Object>> rows,boolean feature) {
        long heartbeatWindow = runtimeConfig.reportingWindowMillis("heartbeat");
        var dailyTraffic = new ArrayList<DailyTrafficDelta>();
        for (int offset=0;offset<rows.size();offset+=BATCH_SIZE) {
            var chunk=rows.subList(offset,Math.min(offset+BATCH_SIZE,rows.size()));
            var keys=chunk.stream().map(p -> p.get("key").toString()).sorted().toList();
            String delivery=key(uid+":"+feature+":"+heartbeatWindow+":"+String.join(";",keys));
            if (cache != null && cache.alreadyCommitted(delivery)) continue;
            if (feature) {
                batchMapper.features(uid,chunk);
            } else {
                dailyTraffic.addAll(batchMapper.bans(uid,chunk,heartbeatWindow));
            }
            if (cache != null) cache.markAfterCommit(delivery);
        }
        writeDailyTraffic(dailyTraffic);
    }

    private void writeDailyTraffic(List<DailyTrafficDelta> rows) {
        // Row locks survive each batch. Sort the entire report before splitting the upserts.
        rows.sort(Comparator.comparingLong(DailyTrafficDelta::userId).thenComparing(DailyTrafficDelta::day));
        for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
            batchMapper.upsertDailyTraffic(rows.subList(offset, Math.min(offset + BATCH_SIZE, rows.size())));
        }
    }
}
