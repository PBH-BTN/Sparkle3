package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BittorrentTrafficMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class BittorrentTrafficSnapshotService {
    private static final int SNAPSHOT_DAYS = 8;
    private static final int RETENTION_DAYS = 35;

    private final BittorrentTrafficMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final Object refreshLock = new Object();

    public BittorrentTrafficSnapshotService(
            BittorrentTrafficMapper mapper,
            PlatformTransactionManager transactionManager
    ) {
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void refresh() {
        synchronized (refreshLock) {
            OffsetDateTime endAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.HOURS)
                    .plusHours(1);
            OffsetDateTime startAt = endAt.minusDays(SNAPSHOT_DAYS);
            OffsetDateTime cutoff = endAt.minusDays(RETENTION_DAYS);
            AtomicInteger deleted = new AtomicInteger();
            AtomicInteger inserted = new AtomicInteger();

            transactionTemplate.executeWithoutResult(status -> {
                deleted.set(mapper.deleteSwarmTrafficSnapshot(startAt, endAt));
                inserted.set(mapper.insertSwarmTrafficSnapshot(startAt, endAt));
                mapper.deleteExpiredSwarmTrafficSnapshot(cutoff);
            });

            log.info(
                    "Refreshed BitTorrent swarm traffic snapshot for {} to {} "
                            + "(deleted={}, inserted={})",
                    startAt,
                    endAt,
                    deleted.get(),
                    inserted.get()
            );
        }
    }
}
