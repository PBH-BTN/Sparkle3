package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeoIpBackfillService {
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final JdbcTemplate jdbcTemplate;
    private final IPDBManager ipdbManager;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void scheduleBackfill() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                int updated = 0;
                for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                    int batchUpdated = backfillBatch();
                    updated += batchUpdated;
                    if (batchUpdated < BATCH_SIZE) {
                        break;
                    }
                    Thread.sleep(100);
                }
                if (updated > 0) {
                    log.info("GeoIP callback enriched {} swarm_tracker rows", updated);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                log.warn("Unable to enrich swarm_tracker GeoIP data: {}", exception.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    private int backfillBatch() throws Exception {
        List<String> peerIps = jdbcTemplate.queryForList("""
                SELECT peer_ip
                FROM public.swarm_tracker
                WHERE peer_geoip IS NULL
                  AND last_time_seen >= NOW() - INTERVAL '7 days'
                GROUP BY peer_ip
                ORDER BY peer_ip
                LIMIT ?
                """, String.class, BATCH_SIZE);
        if (peerIps.isEmpty()) {
            return 0;
        }

        List<GeoUpdate> updates = new ArrayList<>(peerIps.size());
        for (String peerIp : peerIps) {
            IPGeoData geoData = resolveGeoData(peerIp);
            String json = geoData == null ? "{}" : objectMapper.writeValueAsString(geoData);
            updates.add(new GeoUpdate(peerIp, json));
        }

        int[] counts = jdbcTemplate.batchUpdate("""
                UPDATE public.swarm_tracker
                SET peer_geoip = ?::jsonb
                WHERE peer_ip = ?::inet
                  AND peer_geoip IS NULL
                  AND last_time_seen >= NOW() - INTERVAL '7 days'
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                GeoUpdate update = updates.get(index);
                PGobject json = new PGobject();
                json.setType("jsonb");
                json.setValue(update.geoJson());
                statement.setObject(1, json);
                statement.setObject(2, update.peerIp());
            }

            @Override
            public int getBatchSize() {
                return updates.size();
            }
        });
        int updated = 0;
        for (int count : counts) {
            if (count > 0) {
                updated += count;
            }
        }
        return updated;
    }

    private IPGeoData resolveGeoData(String peerIp) {
        try {
            var response = ipdbManager.queryIPDB(InetAddress.ofLiteral(peerIp));
            if (response == null || response.geoData() == null) {
                return null;
            }
            return response.geoData().get();
        } catch (Exception exception) {
            return null;
        }
    }

    private record GeoUpdate(String peerIp, String geoJson) {
    }
}
