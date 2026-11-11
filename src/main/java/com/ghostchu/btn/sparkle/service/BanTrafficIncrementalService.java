package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class BanTrafficIncrementalService {
    private static final String TASK = "ban-connections";
    private static final int BACKFILL_DAYS = 7;
    private static final int RETENTION_DAYS = 8;
    private static final Duration INCREMENTAL_OVERLAP = Duration.ofMinutes(10);

    private final BittorrentTrafficMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final TaskStatusService taskStatusService;
    private final Object refreshLock = new Object();

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleStartupRefresh() {
        taskStatusService.configure(TaskDefinition.startup(
                "ban-traffic-startup-refresh",
                TaskGroup.STARTUP,
                "admin.tasks.items.ban-traffic-startup-refresh.name",
                "admin.tasks.items.ban-traffic-startup-refresh.description"
        ));
        Thread.startVirtualThread(() -> {
            TaskStatusService.TaskExecution execution = taskStatusService.begin(
                    "ban-traffic-startup-refresh",
                    "STARTUP"
            );
            try {
                Thread.sleep(Duration.ofSeconds(20));
                refresh();
                execution.success("BitTorrent traffic incremental state initialized");
            } catch (InterruptedException exception) {
                execution.failure(exception);
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                execution.failure(exception);
                log.error("Unable to initialize incremental BitTorrent traffic state", exception);
            }
        });
    }

    public void refresh() {
        synchronized (refreshLock) {
            State state = loadState();
            if (state == null) {
                log.warn("BitTorrent traffic incremental state is missing");
                return;
            }
            if (!state.ready()) {
                backfill(state);
            } else {
                refreshIncrementally(state);
            }
        }
    }

    private void backfill(@NotNull State state) {
        log.info("Starting seven-day BitTorrent traffic backfill from {}", state.backfillCursor());
        executeInTransaction(() -> {
            jdbcTemplate.execute(
                    "ALTER TABLE public.dashboard_ban_connection_traffic "
                            + "DISABLE TRIGGER dashboard_ban_connection_traffic_hourly_trigger"
            );
            return null;
        });

        OffsetDateTime cursor = state.backfillCursor();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        while (cursor.isBefore(now)) {
            OffsetDateTime chunkEnd = cursor.plusDays(1);
            if (chunkEnd.isAfter(now)) {
                chunkEnd = now;
            }
            OffsetDateTime finalChunkStart = cursor;
            OffsetDateTime finalChunkEnd = chunkEnd;
            int updated = executeInTransaction(() -> {
                int count = mapper.upsertBanConnectionBackfill(finalChunkStart, finalChunkEnd);
                updateCursor(finalChunkEnd);
                return count;
            });
            log.info("Backfilled BitTorrent connection traffic {} to {} ({} rows)",
                    cursor, finalChunkEnd, updated);
            cursor = finalChunkEnd;
            try {
                Thread.sleep(Duration.ofMillis(200));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = completedAt.minusDays(RETENTION_DAYS);
        executeInTransaction(() -> {
            mapper.deleteExpiredBanConnections(cutoff);
            mapper.clearBanCityHourlyTraffic();
            mapper.rebuildBanCityHourlyTraffic(cutoff);
            jdbcTemplate.execute(
                    "ALTER TABLE public.dashboard_ban_connection_traffic "
                            + "ENABLE TRIGGER dashboard_ban_connection_traffic_hourly_trigger"
            );
            jdbcTemplate.update(
                    "DELETE FROM public.dashboard_ban_city_hourly_traffic WHERE hour < ?",
                    cutoff
            );
            mapper.deleteBanTrafficSnapshot();
            mapper.insertBanTrafficSnapshotFromDaily();
            jdbcTemplate.update("""
                    UPDATE public.dashboard_bittorrent_traffic_refresh_state
                    SET backfill_cursor = ?,
                        watermark = ?,
                        ready = TRUE,
                        updated_at = NOW()
                    WHERE task = ?
                    """, completedAt, completedAt, TASK);
            return null;
        });
        log.info("Completed incremental BitTorrent traffic backfill");
    }

    private void refreshIncrementally(@NotNull State state) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime startAt = state.watermark().minus(INCREMENTAL_OVERLAP);
        if (!startAt.isBefore(now)) {
            return;
        }

        OffsetDateTime cutoff = now.minusDays(RETENTION_DAYS);
        int updated = executeInTransaction(() -> {
            int count = mapper.upsertBanConnectionsIncremental(startAt, now);
            mapper.deleteExpiredBanConnections(cutoff);
            jdbcTemplate.update(
                    "DELETE FROM public.dashboard_ban_city_hourly_traffic WHERE hour < ?",
                    cutoff
            );
            mapper.deleteBanTrafficSnapshot();
            mapper.insertBanTrafficSnapshotFromDaily();
            jdbcTemplate.update("""
                    UPDATE public.dashboard_bittorrent_traffic_refresh_state
                    SET watermark = ?,
                        updated_at = NOW()
                    WHERE task = ?
                    """, now, TASK);
            return count;
        });
        log.info("Incrementally refreshed BitTorrent connection traffic from {} to {} ({} rows)",
                startAt, now, updated);
    }

    private State loadState() {
        var states = jdbcTemplate.query("""
                SELECT backfill_cursor, watermark, ready
                FROM public.dashboard_bittorrent_traffic_refresh_state
                WHERE task = ?
                """, (resultSet, rowNum) -> new State(
                resultSet.getObject("backfill_cursor", OffsetDateTime.class),
                resultSet.getObject("watermark", OffsetDateTime.class),
                resultSet.getBoolean("ready")
        ), TASK);
        return states.isEmpty() ? null : states.getFirst();
    }

    private void updateCursor(OffsetDateTime cursor) {
        jdbcTemplate.update("""
                UPDATE public.dashboard_bittorrent_traffic_refresh_state
                SET backfill_cursor = ?,
                    updated_at = NOW()
                WHERE task = ?
                """, cursor, TASK);
    }

    private <T> T executeInTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private record State(
            OffsetDateTime backfillCursor,
            OffsetDateTime watermark,
            boolean ready
    ) {
    }
}
