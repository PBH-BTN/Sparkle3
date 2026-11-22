package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.handler.GlobalExceptionHandler;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryLookupService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQuerySettingsService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQuerySettingsService.Settings;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IpQueryApiControllerTest {
    private final IpQueryCommentMapper mapper = mock(IpQueryCommentMapper.class);
    private final IpQuerySettingsService settings = mock(IpQuerySettingsService.class);
    private final IpQueryLookupService lookup = new IpQueryLookupService(mock(QueryIpServiceImpl.class),
            mock(IPDBManager.class), settings, mock(AiModerationService.class), mapper);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new IpQueryApiController(lookup, mock(IpQueryCommentService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @ParameterizedTest
    @CsvSource({
            "192.0.2.123,192.0.2.0/24",
            "192.0.2.123/32,192.0.2.123/32",
            "2001:db8:1234:5600::1,2001:db8:1234:5600::/56",
            "2001:db8:1234::/48,2001:db8:1234::/48"
    })
    void returnsTheApprovedCountForTheDefaultDiscussion(String query, String commentScope) throws Exception {
        when(settings.get()).thenReturn(new Settings(true, true, 5000, 30));
        when(mapper.countApprovedInScope(commentScope)).thenReturn(3L);

        mvc.perform(get("/api/ui/ip-query/lookup").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCommentScope").value(commentScope))
                .andExpect(jsonPath("$.commentsCount").value(3));
    }

    @Test
    void returnsZeroWhenDiscussionsAreHidden() throws Exception {
        when(settings.get()).thenReturn(new Settings(true, false, 5000, 30));

        mvc.perform(get("/api/ui/ip-query/lookup").param("query", "192.0.2.123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCommentScope").value("192.0.2.0/24"))
                .andExpect(jsonPath("$.commentsCount").value(0));
        verifyNoInteractions(mapper);
    }
}
