package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RulePreviewServiceTest {
    @Test
    void includesIpProvidersAndEveryPeerRuleType() {
        IPDenyListRuleProvider denyProvider = mock(IPDenyListRuleProvider.class);
        when(denyProvider.getContent()).thenReturn("deny.example");

        IPAllowListRuleProvider allowProvider = mock(IPAllowListRuleProvider.class);
        when(allowProvider.getContent()).thenReturn("allow.example");

        IRuleService ruleService = mock(IRuleService.class);
        when(ruleService.list(org.mockito.ArgumentMatchers.<Wrapper<Rule>>any())).thenReturn(List.of(
                new Rule().setType("peer_id").setCategory("peer").setContent("-PB0001-"),
                new Rule().setType("client_name").setCategory("client").setContent("BadClient/1.*"),
                new Rule().setType("port").setCategory("port").setContent("6881"),
                new Rule().setType("script").setCategory("script").setContent("return true")
        ));

        RulePreviewService service = new RulePreviewService(
                List.of(denyProvider),
                List.of(allowProvider),
                ruleService
        );
        var sections = service.preview().sections();

        assertEquals(
                List.of("ip_denylist", "ip_allowlist", "peer_id", "client_name", "port", "script"),
                sections.stream().map(section -> section.type()).toList()
        );
        assertEquals("deny.example", sections.get(0).entries().getFirst().content());
        assertEquals("allow.example", sections.get(1).entries().getFirst().content());
        sections.subList(2, sections.size())
                .forEach(section -> assertEquals(1, section.entries().size()));
    }
}
