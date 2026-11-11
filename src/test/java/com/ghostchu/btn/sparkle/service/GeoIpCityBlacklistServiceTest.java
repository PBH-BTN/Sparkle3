package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.GeoIpCityBlacklistEntry;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoNetworkRecord;
import com.maxmind.db.Network;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoIpCityBlacklistServiceTest {
    private final GeoIpCityBlacklistService service =
            new GeoIpCityBlacklistService(null, null, null);

    @Test
    void matchesChineseNamesWithOptionalAdministrativeSuffixes() {
        GeoIpCityBlacklistEntry entry = new GeoIpCityBlacklistEntry()
                .setCountryCode("CN")
                .setProvince("海南")
                .setCity("海口");
        GeoNetworkRecord record = new GeoNetworkRecord(
                new Network(InetAddress.ofLiteral("1.2.3.0"), 24),
                "CN",
                Set.of("海南省"),
                Set.of("海口市")
        );

        assertTrue(service.matches(entry, record));
        assertFalse(service.matches(entry.setCountryCode("US"), record));
    }

    @Test
    void mergesAdjacentIpv4AndIpv6Networks() {
        var mergedIpv4 = service.mergeNetworks(List.of(
                IPAddressUtil.getIPAddress("10.0.0.0/25"),
                IPAddressUtil.getIPAddress("10.0.0.128/25")
        ));
        var mergedIpv6 = service.mergeNetworks(List.of(
                IPAddressUtil.getIPAddress("2001:db8::/65"),
                IPAddressUtil.getIPAddress("2001:db8:0:0:8000::/65")
        ));

        assertEquals(List.of("10.0.0.0/24"), mergedIpv4.stream()
                .map(network -> network.toCompressedString())
                .toList());
        assertEquals(List.of("2001:db8::/64"), mergedIpv6.stream()
                .map(network -> network.toCompressedString())
                .toList());
    }
}
