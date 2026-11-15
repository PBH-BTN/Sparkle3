package com.ghostchu.btn.sparkle.controller.ui;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.BanHistoryApiController;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.mapper.BanHistoryMapper;
import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BanHistoryApiControllerTest {
    @Test
    void filtersSortsAndReturnsBanOccurrenceTime() throws Exception {
        var start = OffsetDateTime.parse("2026-11-14T00:00:00Z");
        var end = OffsetDateTime.parse("2026-11-15T00:00:00Z");
        var mapper = mock(BanHistoryMapper.class);
        doAnswer(invocation -> {
            Page<BanHistory> page = invocation.getArgument(0);
            QueryWrapper<BanHistory> query = invocation.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("populate_time >="));
            assertTrue(sql.contains("populate_time <="));
            assertTrue(sql.endsWith("ORDER BY populate_time DESC"));
            assertFalse(sql.contains("insert_time"));
            assertEquals(2, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue(start));
            assertTrue(query.getParamNameValuePairs().containsValue(end));
            return page.setRecords(List.of(new BanHistory().setId(1L)
                    .setPopulateTime(start.plusHours(3)).setInsertTime(end.plusHours(1))));
        }).when(mapper).selectPage(any(), any());

        var service = new BanHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var controller = new BanHistoryApiController();
        ReflectionTestUtils.setField(controller, "banHistoryService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/banhistory/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"populateTimeStart":"2026-11-14T00:00:00Z","populateTimeEnd":"2026-11-15T00:00:00Z"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].populateTime").value("2026-11-14T03:00:00Z"))
                .andExpect(jsonPath("$.records[0].insertTime").doesNotExist());
    }
}
