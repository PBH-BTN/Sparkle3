package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RulePreviewServiceTest {
    @Test
    void resolvesCglibProxiesToProviderSource() {
        GeoIpCityBlacklistService target =
                new GeoIpCityBlacklistService(
                        null,
                        null,
                        null,
                        mock(TaskStatusService.class)
                );
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        RulePreviewService service =
                new RulePreviewService(List.of(), List.of(), null);

        assertEquals(
                "geoip-city-blacklist",
                service.providerSource(proxyFactory.getProxy())
        );
    }

    @Test
    void keepsEmptyProvidersVisible() {
        RulePreviewService service =
                new RulePreviewService(List.of(), List.of(), null);
        IPDenyListRuleProvider emptyProvider = new IPDenyListRuleProvider() {
            @Override
            public String getVersion() {
                return null;
            }

            @Override
            public String getContent() {
                return null;
            }
        };

        var entries = service.providerEntries(List.of(emptyProvider));

        assertTrue(entries.size() == 1);
        assertEquals("", entries.getFirst().content());
    }
}
