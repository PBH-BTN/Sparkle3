package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtnGeoIpWindowTest {
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final Duration WINDOW = Duration.ofMillis(100);

    @Test
    void usesRollingWindowAndIncludesExactBoundaryAcrossBatches() {
        var first = observation(50, 4, "CN", "广东", false);
        var boundary = observation(150, 4, "CN", "浙江", true);
        var after = observation(151, 4, "CN", "浙江", true);
        assertEquals(List.of("CN/广东", "CN/浙江"),
                BtnGeoIpWindow.detect(List.of(first, boundary), WINDOW, 2, true).getFirst().evidence());
        assertTrue(BtnGeoIpWindow.detect(List.of(first, after), WINDOW, 2, true).isEmpty());
    }

    @Test
    void canCountAddressFamiliesTogether() {
        var rows = List.of(observation(1, 4, "US", "WA", true), observation(2, 6, "DE", "BE", true));
        assertTrue(BtnGeoIpWindow.detect(rows, WINDOW, 2, true).isEmpty());
        assertEquals(0, BtnGeoIpWindow.detect(rows, WINDOW, 2, false).getFirst().family());
    }

    @Test
    void acceptsLateObservationAndCountsKnownProvincesWithoutDuplicatingUnknownProvince() {
        var rows = List.of(observation(1, 4, "US", "", true), observation(2, 4, "US", "WA", false),
                observation(3, 4, "US", "WA", false), observation(4, 4, "DE", "", false),
                observation(5, 4, "US", "CA", false));
        assertEquals(3, BtnGeoIpWindow.detect(rows, WINDOW, 3, true).getFirst().regions());
        assertTrue(BtnGeoIpWindow.detect(rows, WINDOW, 4, true).isEmpty());
    }

    @Test
    void usesProvinceDataAndRecognizesUnknownCountry() {
        var geo = new IPGeoData();
        var addr = InetAddress.ofLiteral("2001:db8::1");
        assertNull(BtnAppGeoIpRiskService.Region.from(addr, geo));
        var province = new IPGeoData.CityData();
        province.setSubdivisionIso("CA");
        geo.setCity(province);
        geo.setCountry(new IPGeoData.CountryData("United States", "US"));
        assertEquals(new BtnAppGeoIpRiskService.Region(6, "US", "CA"), BtnAppGeoIpRiskService.Region.from(addr, geo));
        province.setCnProvince("浙江省");
        geo.setCountry(new IPGeoData.CountryData("China", "CN"));
        assertEquals(new BtnAppGeoIpRiskService.Region(4, "CN", "浙江省"),
                BtnAppGeoIpRiskService.Region.from(InetAddress.ofLiteral("192.0.2.1"), geo));
    }

    private BtnGeoIpWindow.Observation observation(long milliseconds, int family, String country,
                                                    String province, boolean pending) {
        return new BtnGeoIpWindow.Observation(milliseconds, 1L, START.plusNanos(milliseconds * 1_000_000),
                family, country, province, pending);
    }
}
