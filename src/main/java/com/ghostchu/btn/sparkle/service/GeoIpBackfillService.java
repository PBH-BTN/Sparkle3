package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeoIpBackfillService {
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_CYCLE = 10;

    private final JdbcTemplate jdbcTemplate;
    private final IPDBManager ipdbManager;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean indexReady = new AtomicBoolean();
    private volatile boolean shuttingDown;

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                // Leave the startup path, Flyway and initial API traffic untouched.
                Thread.sleep(Duration.ofSeconds(60));
                ensureBackfillIndex();
                while (!shuttingDown) {
                    int distinctIps = runBackfillCycle();
                    Thread.sleep(distinctIps == 0
                            ? Duration.ofSeconds(60)
                            : Duration.ofSeconds(1));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                log.warn("GeoIP background callback stopped unexpectedly", exception);
            }
        });
    }

    @PreDestroy
    public void stop() {
        shuttingDown = true;
    }

    private void ensureBackfillIndex() {
        if (!indexReady.compareAndSet(false, true)) {
            return;
        }
        try {
            jdbcTemplate.execute("""
                    CREATE INDEX CONCURRENTLY IF NOT EXISTS swarm_tracker_null_geoip_peer_idx
                    ON public.swarm_tracker (peer_ip)
                    WHERE peer_geoip IS NULL
                    """);
        } catch (Exception exception) {
            indexReady.set(false);
            log.warn("Unable to create GeoIP backfill index outside startup; "
                    + "the callback will continue with the existing indexes: {}",
                    exception.getMessage());
        }
    }

    private int runBackfillCycle() throws Exception {
        int updatedRows = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_CYCLE; batch++) {
            BatchResult result = backfillBatch();
            updatedRows += result.updatedRows();
            if (result.distinctIps() < BATCH_SIZE) {
                break;
            }
            Thread.sleep(100);
        }
        if (updatedRows > 0) {
            log.info("GeoIP callback enriched {} swarm_tracker rows", updatedRows);
        }
        return updatedRows;
    }

    private BatchResult backfillBatch() throws Exception {
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
            return new BatchResult(0, 0);
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
        return new BatchResult(peerIps.size(), updated);
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

    private record BatchResult(int distinctIps, int updatedRows) {
    }
}
