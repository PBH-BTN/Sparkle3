package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.UserCareerMapper;
import com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserCareerServiceTest {
    @Test
    void snapshotCacheAvoidsAggregateQueriesButStillChecksPrivacy() throws Exception {
        var profiles = mock(UserPublicProfileService.class);
        var mapper = mock(UserCareerMapper.class);
        var traffic = mock(UserLifetimeTrafficMapper.class);
        var cache = mock(CareerRedisCache.class);
        var target = new User().setId(2L).setRegisterAt(OffsetDateTime.now().minusDays(10));
        when(profiles.requireUser(2)).thenReturn(target);
        when(profiles.privacySettings(target)).thenReturn(UserPrivacySettings.defaults(2));
        when(cache.snapshotKey(2)).thenReturn("snapshot");
        when(cache.get("snapshot",UserCareerService.Snapshot.class)).thenReturn(
                new UserCareerService.Snapshot(Map.of("ban","42"),null,null));
        var service = new UserCareerService(profiles,mapper,traffic);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"cache",cache);
        assertEquals("42",service.read(2,2).metrics().get("ban"));
        verifyNoInteractions(mapper,traffic);
        assertNull(service.read(1,2));
        verify(profiles).privacySettings(target);
    }

    @Test
    void hiddenCareerNeverQueriesStatisticsForAnotherUser() throws Exception {
        var profiles = mock(UserPublicProfileService.class);
        var mapper = mock(UserCareerMapper.class);
        var traffic = mock(UserLifetimeTrafficMapper.class);
        var target = new User().setId(2L).setRegisterAt(OffsetDateTime.now().minusDays(10));
        when(profiles.requireUser(2)).thenReturn(target);
        when(profiles.privacySettings(target)).thenReturn(UserPrivacySettings.defaults(2));
        assertNull(new UserCareerService(profiles, mapper, traffic).read(1, 2));
        verifyNoInteractions(mapper, traffic);
    }

    @Test
    void ownerSeesExactCountersAndRegistrationDays() throws Exception {
        var profiles = mock(UserPublicProfileService.class);
        var mapper = mock(UserCareerMapper.class);
        var traffic = mock(UserLifetimeTrafficMapper.class);
        when(profiles.requireUser(2)).thenReturn(new User().setId(2L)
                .setRegisterAt(OffsetDateTime.now().minusDays(10)));
        when(mapper.counts(2)).thenReturn(List.of(Map.of("kind", "ban", "total", "9007199254740993", "recent", "3")));
        when(mapper.durations(2)).thenReturn(Map.of("seeding", "3600", "downloading", "1800"));
        var view = new UserCareerService(profiles, mapper, traffic).read(2, 2);
        assertEquals("9007199254740993", view.metrics().get("ban"));
        assertEquals("3", view.metrics().get("banRecent"));
        assertEquals("10", view.metrics().get("days"));
        assertEquals("3600", view.metrics().get("seeding"));
        assertNull(view.observerName());
    }
}
