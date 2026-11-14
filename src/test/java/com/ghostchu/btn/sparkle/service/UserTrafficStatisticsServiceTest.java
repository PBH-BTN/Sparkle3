package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserTrafficStatisticsServiceTest {
    @Test
    void pagesThroughFinalResultsWithoutHistoryLimitAndPreservesExactBytes() {
        var mapper=mock(UserTrafficStatisticsMapper.class);
        when(mapper.count(7)).thenReturn(1000L);
        when(mapper.daily(7,990,30)).thenReturn(List.of(Map.of(
                "day","2020-01-01","upload","9007199254740993","seeding","12.5")));
        var result=new UserTrafficStatisticsService(mapper).daily(7,34,30);
        assertEquals(1000,result.total());
        assertEquals(34,result.pages());
        assertEquals("2020-01-01",result.records().getFirst().day());
        assertEquals("9007199254740993",result.records().getFirst().metrics().get("upload"));
        assertEquals("12.5",result.records().getFirst().metrics().get("seeding"));
        assertFalse(result.records().getFirst().metrics().containsKey("day"));
        assertTrue(new UserTrafficStatisticsService(mapper).daily(7,Long.MAX_VALUE,30).records().isEmpty());
        verify(mapper,times(1)).daily(anyLong(),anyLong(),anyInt());
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        var mapper=mock(UserTrafficStatisticsMapper.class);
        var service=new UserTrafficStatisticsService(mapper);
        assertThrows(ResponseStatusException.class,()->service.daily(7,0,30));
        assertThrows(ResponseStatusException.class,()->service.daily(7,1,101));
        verifyNoInteractions(mapper);
    }
}
