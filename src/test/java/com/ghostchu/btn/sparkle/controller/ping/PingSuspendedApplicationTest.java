package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.controller.handler.GlobalExceptionHandler;
import com.ghostchu.btn.sparkle.controller.ping.dto.BtnConfig;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.service.BtnReportVolumeService;
import com.ghostchu.btn.sparkle.service.IUserappConfigService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentCountService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PingSuspendedApplicationTest {
    @Test
    void suspendedAppCanFetchWhileEverySubmissionReturnsForbidden() throws Exception {
        var app = new Userapp().setId(1L).setOwner(2L).setAppId("test-app")
                .setBannedAt(OffsetDateTime.now()).setBannedReason("Replay limit exceeded");
        var apps = mock(IUserappService.class);
        var request = new MockHttpServletRequest();
        request.addHeader("X-BTN-AppID", "test-app");
        request.addHeader("X-BTN-AppSecret", "test-secret");
        when(apps.loginViaCredential("test-app", "test-secret", null,
                InetAddress.ofLiteral(request.getRemoteAddr()), null)).thenReturn(app);

        var settings = mock(BtnRuntimeConfigService.class);
        var clientConfig = mock(IUserappConfigService.class);
        when(clientConfig.configLoggedInUserapp(app)).thenReturn(new BtnConfig(1, 2, Map.of(), null));
        when(settings.text("rootUrls", "rootUrl")).thenReturn("https://btn.example");
        var config = new PingConfigController();
        setField(config, "configService", settings);
        setField(config, "userappConfigService", clientConfig);
        setField(config, "objectMapper", JsonMapper.builder().build());

        var rules = new PingRuleIpController();
        var ruleProvider = mock(IPDenyListRuleProvider.class);
        when(ruleProvider.getVersion()).thenReturn("test-version");
        when(ruleProvider.getContent()).thenReturn("203.0.113.0/24");
        setField(rules, "configService", settings);
        setField(rules, "ipDenyListRuleProviders", List.of(ruleProvider));
        setField(rules, "ipAllowListRuleProviders", List.of());

        var legacyLookup = new PingQueryIpController();
        setField(legacyLookup, "configService", settings);
        setField(legacyLookup, "queryIpService", mock(QueryIpServiceImpl.class));
        var counts = mock(IpQueryCommentCountService.class);
        when(counts.count(1L, List.of("192.0.2.1"))).thenReturn(Map.of("192.0.2.1", 3L));
        var details = new PingIpQueryDetailsController(settings, null, null, null, counts, null);
        var heartbeat = new PingHeartbeatController();
        var bans = new PingSubmitBansController();
        var swarm = new PingSwarmController();
        var volume = mock(BtnReportVolumeService.class);
        var heartbeatService = mock(IUserappsHeartbeatService.class);
        setField(heartbeat, "heartbeatService", heartbeatService);
        for (var controller : List.of(heartbeat, bans, swarm)) {
            setField(controller, "configService", settings);
            setField(controller, "reportVolume", volume);
        }
        var points = mock(PointsService.class);
        var controllers = List.of(config, rules, legacyLookup, details, heartbeat, bans, swarm);
        for (var controller : controllers) {
            controller.request = request;
            setField(controller, "userappService", apps);
            setField(controller, "pointsService", points);
        }
        var mvc = MockMvcBuilders.standaloneSetup(controllers.toArray())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/ping/config").param("forceRoute", "global"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.min_protocol_version").value(1));
        mvc.perform(get("/ping/ruleIpDenylist").param("rev", "old-version"))
                .andExpect(status().isOk()).andExpect(content().string("203.0.113.0/24"));
        mvc.perform(get("/ping/ruleIpAllowlist").param("rev", "old-version")).andExpect(status().isOk());
        mvc.perform(get("/ping/queryIp").param("ip", "192.0.2.1")).andExpect(status().isOk());
        mvc.perform(post("/ping/queryIp/commentCounts").contentType(APPLICATION_JSON).content("[\"192.0.2.1\"]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$['192.0.2.1']").value(3));
        for (String path : List.of("/ping/heartbeat", "/ping/syncBanHistory", "/ping/syncSwarm", "/ping/queryIp/comments")) {
            mvc.perform(post(path).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(put("/ping/queryIp/comments/1").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/ping/queryIp/comments/1")).andExpect(status().isForbidden());
        verifyNoInteractions(heartbeatService, volume, points);
    }
}
