package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.UserNotificationPreferenceMapper;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailUnsubscribeServiceTest {
    private final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final IUserService users = mock(IUserService.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final BtnRuntimeConfigService config = mock(BtnRuntimeConfigService.class);
    private final Map<String, String> storage = new HashMap<>();
    private final MailUnsubscribeService service = new MailUnsubscribeService(redis, users, preferences, config);

    MailUnsubscribeServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> storage.get(call.getArgument(0)));
        doAnswer(call -> {
            storage.put(call.getArgument(0), call.getArgument(1));
            assertEquals(Duration.ofDays(90), call.getArgument(2));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(config.text("rootUrls", "rootUrl")).thenReturn("https://example.com/");
        when(users.getById(7L)).thenReturn(new User().setId(7L).setEmail("test@example.com"));
    }

    private String token() {
        String link = service.link(7L, "test@example.com", NotificationCategory.CLIENT_SECURITY);
        assertTrue(link.startsWith("https://example.com/mail-unsubscribe#token="));
        return link.split("token=")[1].split("&")[0];
    }

    @Test
    void onlyListsOptionalCategoriesWithoutChangingPreferences() {
        when(preferences.list(7L)).thenReturn(List.of(
                NotificationPreference.defaults(NotificationCategory.ACCOUNT_SECURITY),
                NotificationPreference.defaults(NotificationCategory.CLIENT_SECURITY)));
        assertEquals(List.of(new com.ghostchu.btn.sparkle.controller.ui.dto.MailUnsubscribeModels.Option("CLIENT_SECURITY", true)), service.options(token()));
        verify(preferences, never()).disableEmail(anyLong(), anyList());
    }

    @Test
    void credentialOnlyAllowsDisablingSelectedEmailCategories() {
        service.unsubscribe(token(), List.of("CLIENT_SECURITY", "DIRECT_MESSAGE"));
        verify(preferences).disableEmail(7L, List.of(NotificationCategory.CLIENT_SECURITY, NotificationCategory.DIRECT_MESSAGE));
    }

    @Test
    void rejectsMandatoryAndUnknownCategoriesAtomically() {
        String token = token();
        for (String category : List.of("ACCOUNT_SECURITY", "LEGAL_COMPLIANCE", "unknown")) {
            assertThrows(ResponseStatusException.class, () -> service.unsubscribe(token, List.of("DIRECT_MESSAGE", category)));
        }
        verify(preferences, never()).disableEmail(anyLong(), anyList());
    }

    @Test
    void rejectsInvalidExpiredDeletedAndChangedEmailCredentials() {
        assertThrows(ResponseStatusException.class, () -> service.options("bad"));
        String token = token();
        assertThrows(ResponseStatusException.class, () -> service.options("a".repeat(43)));
        when(users.getById(7L)).thenReturn(new User().setId(7L).setEmail("changed@example.com"));
        assertThrows(ResponseStatusException.class, () -> service.options(token));
        when(users.getById(7L)).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.options(token));
        storage.clear();
        assertThrows(ResponseStatusException.class, () -> service.options(token));
    }

    @Test
    void mandatoryMailHasNoUnsubscribeCredential() {
        assertNull(service.link(7L, "test@example.com", NotificationCategory.ACCOUNT_SECURITY));
        verifyNoInteractions(values);
    }

    @Test
    void updatesOnlyEmailForDistinctSelectedCategories() {
        UserNotificationPreferenceMapper mapper = mock(UserNotificationPreferenceMapper.class);
        NotificationPreferenceService service = new NotificationPreferenceService(mapper);
        service.disableEmail(7L, List.of(NotificationCategory.DIRECT_MESSAGE, NotificationCategory.DIRECT_MESSAGE));
        verify(mapper).disableEmail(7L, "DIRECT_MESSAGE");
        verifyNoMoreInteractions(mapper);
    }
}
