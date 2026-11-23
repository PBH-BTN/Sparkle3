package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.constants.PointCategory;
import com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper;
import com.ghostchu.btn.sparkle.service.BtnAppGeoIpRiskService;
import com.ghostchu.btn.sparkle.service.points.PointAwardBucket;
import com.ghostchu.btn.sparkle.service.points.PointRulesService;
import com.ghostchu.btn.sparkle.service.points.PointToggleService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import com.ghostchu.btn.sparkle.util.LazyLoad;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.InetAddress;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserappsHeartbeatServiceImplTest {
    private final UserAppsHeartbeatMapper mapper = mock(UserAppsHeartbeatMapper.class);
    private final PointsService points = mock(PointsService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final InetAddress ip = InetAddress.ofLiteral("192.0.2.1");

    @Test
    void eachHeartbeatWritesAndSettlesItsOwnTransaction() {
        var service = service();

        service.onHeartBeat(10, 20, ip);
        service.onHeartBeat(10, 20, ip);

        var order = inOrder(transactions, mapper, points);
        for (int request = 0; request < 2; request++) {
            order.verify(transactions).getTransaction(any());
            order.verify(mapper).upsert(argThat(row -> row.getUserappId() == 10 && ip.equals(row.getIp())));
            order.verify(points).commit(argThat(bucket -> bucket.getUserId() == 20
                    && bucket.getContributions().get(PointCategory.KEEPALIVE).getPoints() == 3
                    && bucket.getAppActivities().containsKey(10L) && bucket.getClaims().size() == 1));
            order.verify(transactions).commit(transaction);
        }
    }

    @Test
    void failedPointSettlementRollsBackTheHeartbeat() {
        var service = service();
        doThrow(new IllegalStateException("point settlement failed")).when(points).commit(any(PointAwardBucket.class));

        assertThrows(IllegalStateException.class, () -> service.onHeartBeat(10, 20, ip));

        var order = inOrder(mapper, points, transactions);
        order.verify(mapper).upsert(any());
        order.verify(points).commit(any(PointAwardBucket.class));
        order.verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    private UserappsHeartbeatServiceImpl service() {
        var service = new UserappsHeartbeatServiceImpl();
        var geo = mock(IPDBManager.class);
        var toggles = mock(PointToggleService.class);
        var rules = mock(PointRulesService.class);
        when(geo.queryIPDB(ip)).thenReturn(new IPDBManager.IPDBResponse(new LazyLoad<>(() -> null)));
        when(toggles.isEnabled(any())).thenReturn(true);
        when(rules.points(any())).thenReturn(3L);
        when(points.currentPointDay()).thenReturn(LocalDate.of(2026, 11, 24));
        when(transactions.getTransaction(any())).thenReturn(transaction);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "pointsService", points);
        ReflectionTestUtils.setField(service, "pointToggleService", toggles);
        ReflectionTestUtils.setField(service, "pointRulesService", rules);
        ReflectionTestUtils.setField(service, "geoIPManager", geo);
        ReflectionTestUtils.setField(service, "geoIpRisk", mock(BtnAppGeoIpRiskService.class));
        ReflectionTestUtils.setField(service, "transactionManager", transactions);
        return service;
    }
}
