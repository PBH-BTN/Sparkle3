package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficRefreshMapper;
import com.ghostchu.btn.sparkle.service.dto.TrafficRefreshStateRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
    private final BittorrentTrafficRefreshMapper refreshMapper;
    private final PlatformTransactionManager transactionManager;
    private final Object refreshLock = new Object();

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
            refreshMapper.disableHourlyTrigger();
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
            refreshMapper.enableHourlyTrigger();
            refreshMapper.deleteBanCityHourlyBefore(cutoff);
            mapper.deleteBanTrafficSnapshot();
            mapper.insertBanTrafficSnapshotFromDaily();
            refreshMapper.completeBackfill(TASK, completedAt);
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
            refreshMapper.deleteBanCityHourlyBefore(cutoff);
            mapper.deleteBanTrafficSnapshot();
            mapper.insertBanTrafficSnapshotFromDaily();
            refreshMapper.updateWatermark(TASK, now);
            return count;
        });
        log.info("Incrementally refreshed BitTorrent connection traffic from {} to {} ({} rows)",
                startAt, now, updated);
    }

    private State loadState() {
        TrafficRefreshStateRow row = refreshMapper.selectState(TASK);
        return row == null ? null : new State(
                row.getBackfillCursor(),
                row.getWatermark(),
                Boolean.TRUE.equals(row.getReady())
        );
    }

    private void updateCursor(OffsetDateTime cursor) {
        refreshMapper.updateCursor(TASK, cursor);
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
