package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewSection;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import inet.ipaddr.IPAddressString;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpQueryLabelServiceTest {
    private final RulePreviewService rules = mock(RulePreviewService.class);
    private final IUserappsHeartbeatService heartbeats = mock(IUserappsHeartbeatService.class);
    private final IUserappService apps = mock(IUserappService.class);
    private final IUserService users = mock(IUserService.class);
    private final UserPrivacyService privacy = mock(UserPrivacyService.class);
    private final UserPublicProfileService profiles = new UserPublicProfileService(users, null, privacy);
    private final IpQueryLabelService service = new IpQueryLabelService(rules, heartbeats, apps, users, profiles);
    private final OffsetDateTime since = OffsetDateTime.now();

    @Test
    void matchesAllRuleSourcesAndAddsSeparateBypassLabel() {
        when(rules.ipSections(true)).thenReturn(List.of(
                new RulePreviewSection("ip_denylist", List.of(
                        new RulePreviewEntry("concurrent-download", null, "# comment\n203.0.113.0/24"),
                        new RulePreviewEntry("manual", "示例", "203.0.113.5 - 203.0.113.9 , 100 , test"),
                        new RulePreviewEntry("overdownload", null, "203.0.113.5 - 203.0.113.9 , 200 , test"),
                        new RulePreviewEntry("random-identity", null, "bad ip\n2001:db8::1")
                )),
                new RulePreviewSection("ip_allowlist", List.of(
                        new RulePreviewEntry("btn-bypass", null, "203.0.113.7 # private metadata")
                ))
        ));

        var labels = service.labels(new IPAddressString("203.0.113.7/32").getAddress(), since);
        assertEquals(List.of("IP 黑名单->并发下载分析", "IP 黑名单->示例",
                        "IP 允许列表->BTN Bypass 用户应用", "BTN Bypass"),
                labels.stream().map(IpQueryLabelService.Label::text).toList());
    }

    @Test
    void onlyUsesThePublicNickname() {
        var network = new IPAddressString("203.0.113.7/32").getAddress();
        var user = new User().setId(2L).setNickname("PrivateName");
        when(heartbeats.fetchIpHeartbeatRecords(network.toCanonicalString(), since))
                .thenReturn(List.of(new UserappsHeartbeat().setUserappId(1L)));
        when(apps.getById(1L)).thenReturn(new Userapp().setOwner(2L));
        when(users.getById(2L)).thenReturn(user);
        var defaults = UserPrivacySettings.defaults(2L);
        when(privacy.get(2L)).thenReturn(defaults);
        assertEquals("BTN用户->Pri*******e", service.labels(network, since).getFirst().text());

        when(privacy.get(2L)).thenReturn(new UserPrivacySettings(2L, defaults.avatarVisibility(),
                UsernameVisibility.FULL, defaults.pointsVisibility(), true, defaults.lifetimeTrafficVisibility()));
        assertEquals("BTN用户->PrivateName", service.labels(network, since).getFirst().text());

        var subnet = new IPAddressString("203.0.113.0/24").getAddress();
        when(heartbeats.fetchIpHeartbeatRecords(subnet.toCanonicalString(), since))
                .thenReturn(List.of(new UserappsHeartbeat().setUserappId(1L)));
        assertEquals("BTN用户", service.labels(subnet, since).getFirst().text());

        user.setNicknameModerationStatus("PENDING");
        assertEquals("BTN用户", service.labels(network, since).getFirst().text());
    }
}
