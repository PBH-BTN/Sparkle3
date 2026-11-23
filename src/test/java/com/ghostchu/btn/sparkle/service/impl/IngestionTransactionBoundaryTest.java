package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.controller.ping.dto.BtnSwarm;
import com.ghostchu.btn.sparkle.mapper.CareerBatchMapper;
import com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper;
import com.ghostchu.btn.sparkle.mapper.SwarmTrafficDeltaMapper;
import com.ghostchu.btn.sparkle.service.CareerIngestionService;
import com.ghostchu.btn.sparkle.service.CareerRedisCache;
import com.ghostchu.btn.sparkle.service.HomepageStatisticsRefreshService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.TorrentRegistration;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.points.PointAwardContext;
import com.ghostchu.btn.sparkle.util.LazyLoad;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionTransactionBoundaryTest {
    private final SwarmTrackerServiceImpl service = new SwarmTrackerServiceImpl();
    private final ITorrentService torrents = mock(ITorrentService.class);
    private final IPDBManager geo = mock(IPDBManager.class);
    private final SwarmTrackerMapper mapper = mock(SwarmTrackerMapper.class);
    private final PointAwardContext points = mock(PointAwardContext.class);

    @Test
    void preparesOutsideTransactionAndRollsBackRawRowsWhenCountersFail() {
        var career = mock(CareerIngestionService.class);
        var transactions = mock(PlatformTransactionManager.class);
        var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        var prepared = mock(CareerIngestionService.PreparedSwarm.class);
        when(career.prepareSwarm(eq(1L), anyList())).thenReturn(prepared);
        doThrow(new IllegalStateException("counter write failed")).when(career).swarmBatch(prepared);
        configureService(career, transactions);

        assertThrows(IllegalStateException.class, () -> service.syncSwarm(1, 1, List.of(swarm())));

        var order = inOrder(torrents, geo, transactions, mapper, career);
        order.verify(torrents).getOrCreateTorrent(anyString(), any(), any(), any(), any());
        order.verify(geo).queryIPDB(any());
        order.verify(career).prepareSwarm(eq(1L), anyList());
        order.verify(transactions).getTransaction(any());
        order.verify(mapper).batchUpsert(anyList());
        order.verify(career).swarmBatch(prepared);
        order.verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
        verify(career, never()).markSwarmCommitted(any());
        verifyNoInteractions(points);
    }

    @Test
    void readsRedisBeforeBorrowingAndMarksBatchAfterClosingTheConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var careerMapper = mock(CareerBatchMapper.class);
        var career = new CareerIngestionService(careerMapper, mock(BtnRuntimeConfigService.class),
                new CareerRedisCache(redis));
        configureService(career, new DataSourceTransactionManager(dataSource));

        service.syncSwarm(1, 1, List.of(swarm()));

        var fingerprint = ArgumentCaptor.forClass(String.class);
        var order = inOrder(values, dataSource, mapper, careerMapper, connection, points);
        order.verify(values).get(fingerprint.capture());
        order.verify(dataSource).getConnection();
        order.verify(mapper).batchUpsert(anyList());
        order.verify(careerMapper).swarm(eq(1L), anyList(), anyLong(), anyLong());
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(values).set(eq(fingerprint.getValue()), eq("true"), eq(Duration.ofMinutes(5)));
        order.verify(points).recordConfigured(eq(1L), any(), eq(1L), eq(0L));
    }

    @Test
    void emptySwarmDoesNotBorrowAConnection() {
        var transactions = mock(PlatformTransactionManager.class);
        var career = mock(CareerIngestionService.class);
        configureService(career, transactions);

        service.syncSwarm(1, 1, List.of());

        verifyNoInteractions(transactions, mapper, career, points);
    }

    private void configureService(CareerIngestionService career, PlatformTransactionManager transactions) {
        lenient().when(torrents.getOrCreateTorrent(anyString(), any(), any(), any(), any()))
                .thenReturn(new TorrentRegistration(1, 100));
        lenient().when(geo.queryIPDB(any())).thenReturn(new IPDBManager.IPDBResponse(new LazyLoad<>(() -> null)));
        ReflectionTestUtils.setField(service, "torrentService", torrents);
        ReflectionTestUtils.setField(service, "geoIPManager", geo);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "careerIngestionService", career);
        ReflectionTestUtils.setField(service, "platformTransactionManager", transactions);
        ReflectionTestUtils.setField(service, "homepageStatisticsRefreshService", mock(HomepageStatisticsRefreshService.class));
        ReflectionTestUtils.setField(service, "pointAwardContext", points);
        ReflectionTestUtils.setField(service, "swarmTrafficDeltaMapper", mock(SwarmTrafficDeltaMapper.class));
    }

    private BtnSwarm swarm() {
        var row = new BtnSwarm();
        row.setTorrentIdentifier("test");
        row.setDownloader("client");
        row.setPeerIp("192.0.2.1");
        row.setPort(6881);
        row.setToPeerTraffic(100);
        row.setFirstTimeSeen(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        row.setLastTimeSeen(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
        return row;
    }
}
