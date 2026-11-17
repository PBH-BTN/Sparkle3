package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.mapper.SwarmTrafficDeltaMapper;
import com.ghostchu.btn.sparkle.service.CareerIngestionService;
import com.ghostchu.btn.sparkle.service.HomepageStatisticsRefreshService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.TorrentRegistration;
import com.ghostchu.btn.sparkle.service.points.PointAwardContext;
import com.ghostchu.btn.sparkle.util.LazyLoad;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionTransactionBoundaryTest {
    @Test
    void preparesOutsideTransactionAndRollsBackRawRowsWhenCountersFail() {
        var service = new SwarmTrackerServiceImpl();
        var torrents = mock(ITorrentService.class);
        var geo = mock(IPDBManager.class);
        var mapper = mock(SwarmTrackerMapper.class);
        var career = mock(CareerIngestionService.class);
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(torrents.getOrCreateTorrent(anyString(), any(), any(), any(), any()))
                .thenReturn(new TorrentRegistration(1, 100));
        when(geo.queryIPDB(any())).thenReturn(new IPDBManager.IPDBResponse(new LazyLoad<>(() -> null)));
        doThrow(new IllegalStateException("counter write failed")).when(career).swarmBatch(eq(1L), anyList());
        ReflectionTestUtils.setField(service, "torrentService", torrents);
        ReflectionTestUtils.setField(service, "geoIPManager", geo);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "careerIngestionService", career);
        ReflectionTestUtils.setField(service, "platformTransactionManager", transactions);
        ReflectionTestUtils.setField(service, "homepageStatisticsRefreshService", mock(HomepageStatisticsRefreshService.class));
        ReflectionTestUtils.setField(service, "pointAwardContext", mock(PointAwardContext.class));
        ReflectionTestUtils.setField(service, "swarmTrafficDeltaMapper", mock(SwarmTrafficDeltaMapper.class));
        var row = new BtnSwarm();
        row.setTorrentIdentifier("test");
        row.setDownloader("client");
        row.setPeerIp("192.0.2.1");
        row.setPort(6881);
        row.setToPeerTraffic(100);
        row.setFirstTimeSeen(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        row.setLastTimeSeen(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));

        assertThrows(IllegalStateException.class, () -> service.syncSwarm(1, 1, List.of(row)));

        var order = inOrder(torrents, geo, transactions, mapper, career);
        order.verify(torrents).getOrCreateTorrent(anyString(), any(), any(), any(), any());
        order.verify(geo).queryIPDB(any());
        order.verify(transactions).getTransaction(any());
        order.verify(mapper).batchUpsert(anyList());
        order.verify(career).swarmBatch(eq(1L), anyList());
        order.verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }
}
