package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.*;
import com.ghostchu.btn.sparkle.util.LazyLoad;
import com.ghostchu.btn.sparkle.util.ipdb.*;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserApplicationBypassServiceTest {
    @Test
    void deduplicatesCurrentIpsAndIncludesGeoData() {
        var heartbeats=mock(IUserappsHeartbeatService.class);
        var ipdb=mock(IPDBManager.class);
        var ip=InetAddress.ofLiteral("192.0.2.10");
        var geo=new IPGeoData();
        geo.setCountry(new IPGeoData.CountryData("Example","EX"));
        when(ipdb.queryIPDB(ip)).thenReturn(new IPDBManager.IPDBResponse(new LazyLoad<>(() -> geo)));
        when(heartbeats.fetchHeartBeatsByUserAppIdInTimeRange(eq(1L),any(),any())).thenReturn(List.of(
                new UserappsHeartbeat().setIp(ip),new UserappsHeartbeat().setIp(ip)));
        var service=new UserApplicationBypassService(heartbeats,ipdb);
        var result=service.list(new Userapp().setId(1L).setBtnBypass(true));
        assertEquals(1,result.size());
        assertEquals("192.0.2.10",result.getFirst().ip());
        assertEquals("EX",result.getFirst().peerGeoip().getCountry().getIso());
        verify(ipdb,times(1)).queryIPDB(ip);
    }

    @Test
    void disabledOrBannedApplicationsDoNotExposeIps() {
        var heartbeats=mock(IUserappsHeartbeatService.class);
        var ipdb=mock(IPDBManager.class);
        var service=new UserApplicationBypassService(heartbeats,ipdb);
        assertTrue(service.list(new Userapp().setBtnBypass(false)).isEmpty());
        assertTrue(service.list(new Userapp().setBtnBypass(true).setBannedAt(java.time.OffsetDateTime.now())).isEmpty());
        verifyNoInteractions(heartbeats,ipdb);
    }
}
