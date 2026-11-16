package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterAudience;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterConfigView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterMetricSetting;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterPerformanceView;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.FooterSupportButton;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.FooterConfigService;
import com.ghostchu.btn.sparkle.service.FooterMetricsService;
import com.ghostchu.btn.sparkle.service.FooterVisitorService;
import com.ghostchu.btn.sparkle.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FooterApiControllerTest {
    @Test
    void anonymousVisitorsReceiveSupportImagesInConfiguredOrder() {
        FooterConfigService configService = mock(FooterConfigService.class);
        var buttons = List.of(new FooterSupportButton("支持", "Support", "/support", "/images/a.svg"),
                new FooterSupportButton("爱发电", "Afdian", "https://ifdian.net/a/Ghost_chu", "/images/b.svg"));
        when(configService.get()).thenReturn(new FooterConfigView("", "", FooterAudience.ADMIN,
                "", FooterAudience.ADMIN, List.of(), FooterAudience.ADMIN, FooterAudience.ADMIN,
                FooterAudience.ADMIN, List.of(), buttons));
        var controller = new FooterApiController(configService, mock(FooterMetricsService.class),
                mock(FooterVisitorService.class), mock(IUserService.class));
        var response = controller.footer(null, mock(HttpServletRequest.class));
        assertNotNull(response.getBody());
        assertEquals(buttons, response.getBody().supportButtons());
    }

    @Test
    void currentRolesControlAdminOnlyMetricsForAnExistingSession() {
        FooterConfigService configService = mock(FooterConfigService.class);
        FooterMetricsService metricsService = mock(FooterMetricsService.class);
        FooterVisitorService visitorService = mock(FooterVisitorService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IUserService users = mock(IUserService.class);
        var principal = new SparkleUserDetails(new User().setId(1L).setRole("user"), null);
        when(users.getById(1L)).thenReturn(new User().setRoles(new String[]{"user", "admin", "supporter"}));
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
                List.of(new FooterMetricSetting("loadAverage", true, FooterAudience.ADMIN)),
                List.of()
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
                new FooterApiController(configService, metricsService, visitorService, users);
        var response = controller.footer(principal, request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().performance());
        assertEquals(1.0, response.getBody().performance().loadAverage1());

        when(users.getById(1L)).thenReturn(new User().setRoles(new String[]{"user", "supporter"}));
        assertNull(controller.footer(principal, request).getBody().performance());
    }
}
