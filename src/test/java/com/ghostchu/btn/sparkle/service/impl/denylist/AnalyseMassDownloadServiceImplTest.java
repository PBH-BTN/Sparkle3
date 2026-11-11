package com.ghostchu.btn.sparkle.service.impl.denylist;

import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyseMassDownloadServiceImplTest {
    private final AnalyseMassDownloadServiceImpl service =
            new AnalyseMassDownloadServiceImpl();

    @Test
    void formatsIpv4HostWithoutPrefixAndKeepsSubnets() {
        assertEquals(
                "192.0.2.1",
                service.formatRuleAddress(IPAddressUtil.getIPAddress("192.0.2.1/32"))
        );
        assertEquals(
                "192.0.2.0/24",
                service.formatRuleAddress(IPAddressUtil.getIPAddress("192.0.2.0/24"))
        );
        assertEquals(
                "2001:db8::/56",
                service.formatRuleAddress(IPAddressUtil.getIPAddress("2001:db8::/56"))
        );
    }
}
