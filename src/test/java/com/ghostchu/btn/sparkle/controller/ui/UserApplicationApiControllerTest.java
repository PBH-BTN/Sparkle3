package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserApplicationApiControllerTest {
    @Test
    void listsOnlyCurrentIpsForActiveBypassApplications() {
        IUserappService userappService = mock(IUserappService.class);
        IUserappsHeartbeatService heartbeatService = mock(IUserappsHeartbeatService.class);
        SparkleUserDetails principal = mock(SparkleUserDetails.class);
        when(principal.getUserId()).thenReturn(10L);

        OffsetDateTime now = OffsetDateTime.now();
        Userapp activeBypass = new Userapp()
                .setId(1L)
                .setAppId("active")
                .setBtnBypass(true)
                .setCreatedAt(now);
        Userapp bannedBypass = new Userapp()
                .setId(2L)
                .setAppId("banned")
                .setBtnBypass(true)
                .setBannedAt(now)
                .setCreatedAt(now.minusMinutes(1));
        Userapp regular = new Userapp()
                .setId(3L)
                .setAppId("regular")
                .setBtnBypass(false)
                .setCreatedAt(now.minusMinutes(2));
        when(userappService.getUserAppsByUserId(10L))
                .thenReturn(List.of(activeBypass, bannedBypass, regular));
        when(heartbeatService.fetchHeartBeatsByUserAppIdsInTimeRange(
                anyCollection(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(
                new UserappsHeartbeat()
                        .setUserappId(1L)
                        .setIp(InetAddress.ofLiteral("192.0.2.10"))
                        .setFirstSeenAt(now.minusMinutes(5))
                        .setLastSeenAt(now),
                new UserappsHeartbeat()
                        .setUserappId(1L)
                        .setIp(InetAddress.ofLiteral("2001:db8::10"))
                        .setFirstSeenAt(now.minusMinutes(4))
                        .setLastSeenAt(now)
        ));

        UserApplicationApiController controller =
                new UserApplicationApiController(userappService, heartbeatService);
        var result = controller.list(principal);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> appIds =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(heartbeatService).fetchHeartBeatsByUserAppIdsInTimeRange(
                appIds.capture(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        );
        assertEquals(List.of(1L), List.copyOf(appIds.getValue()));
        assertEquals(
                List.of("192.0.2.10", "2001:db8:0:0:0:0:0:10"),
                result.stream().filter(item -> item.getId() == 1L)
                        .findFirst().orElseThrow().getExemptIps()
        );
        result.stream().filter(item -> item.getId() != 1L)
                .forEach(item -> assertEquals(List.of(), item.getExemptIps()));
    }
}
