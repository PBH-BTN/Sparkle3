package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterAudience;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterMetricSetting;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterPerformanceView;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.FooterConfigService;
import com.ghostchu.btn.sparkle.service.FooterMetricsService;
import com.ghostchu.btn.sparkle.service.FooterVisitorService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FooterApiControllerTest {
    @Test
    void adminAuthorityCanViewAdminOnlyMetricsWithStaleUserRole() {
        FooterConfigService configService = mock(FooterConfigService.class);
        FooterMetricsService metricsService = mock(FooterMetricsService.class);
        FooterVisitorService visitorService = mock(FooterVisitorService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        SparkleUserDetails principal = mock(SparkleUserDetails.class);

        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("admin")))
                .when(principal).getAuthorities();
        when(principal.getUser()).thenReturn(new User().setRole("user"));
        when(configService.get()).thenReturn(new FooterConfigView(
                "",
                "",
                FooterAudience.ADMIN,
                "",
                FooterAudience.ADMIN,
                List.of(),
                FooterAudience.ADMIN,
                FooterAudience.ADMIN,
                FooterAudience.ADMIN,
                List.of(new FooterMetricSetting("loadAverage", true, FooterAudience.ADMIN))
        ));
        when(metricsService.snapshot()).thenReturn(new FooterPerformanceView(
                1.0, 2.0, 3.0,
                10.0, 20.0,
                30L, 40L,
                50.0, 60.0,
                70.0, 80.0,
                90.0, 100.0, 110.0,
                null
        ));

        FooterApiController controller =
                new FooterApiController(configService, metricsService, visitorService);
        var response = controller.footer(principal, request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().performance());
        assertEquals(1.0, response.getBody().performance().loadAverage1());
    }
}
