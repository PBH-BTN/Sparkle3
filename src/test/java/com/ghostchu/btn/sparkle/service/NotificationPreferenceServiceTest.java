package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.UserNotificationPreference;
import com.ghostchu.btn.sparkle.mapper.UserNotificationPreferenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationPreferenceServiceTest {
    private final UserNotificationPreferenceMapper mapper = mock(UserNotificationPreferenceMapper.class);
    private final NotificationPreferenceService service = new NotificationPreferenceService(mapper);
    private static final Set<NotificationCategory> EMAIL_OFF = Set.of(
            NotificationCategory.GENERAL_NOTICE, NotificationCategory.CHANGELOG,
            NotificationCategory.DAILY_ANNOUNCEMENT, NotificationCategory.DATA_EXPORT);

    @Test
    void unsubscribeUsesTheSameCategoryOrderAsSettingsUpdates() {
        var categories = List.of(NotificationCategory.GENERAL_NOTICE, NotificationCategory.CHANGELOG);
        var reversed = new java.util.ArrayList<>(categories);
        reversed.sort(java.util.Comparator.reverseOrder());
        service.disableEmail(1, reversed);
        var order = inOrder(mapper);
        categories.stream().sorted().forEach(category -> order.verify(mapper).disableEmail(1, category.name()));
    }

    @Test
    void unsetPreferencesUseSameDefaultsForDisplayAndDelivery() {
        when(mapper.selectList(any())).thenReturn(List.of());
        for (NotificationPreference preference : service.list(1L)) {
            assertTrue(preference.inboxEnabled());
            assertEquals(!EMAIL_OFF.contains(preference.category()), preference.emailEnabled());
            assertEquals(preference, service.preference(1L, preference.category()));
        }
    }

    @Test
    void savedChoicesOverrideDefaultsExceptForMandatoryCategories() {
        for (NotificationCategory category : NotificationCategory.values()) {
            UserNotificationPreference row = new UserNotificationPreference()
                    .setCategory(category.name()).setInboxEnabled(false).setEmailEnabled(true);
            when(mapper.selectList(any())).thenReturn(List.of(row));
            when(mapper.selectOne(any())).thenReturn(row);
            NotificationPreference preference = service.preference(1L, category);
            assertTrue(preference.emailEnabled());
            assertEquals(category.isInboxRequired(), preference.inboxEnabled());
            assertEquals(preference, service.list(1L).stream()
                    .filter(item -> item.category() == category).findFirst().orElseThrow());
        }
    }

    @Test
    void exportEmailCanBeDisabledWhileInboxStaysEnabled() {
        when(mapper.selectOne(any())).thenReturn(new UserNotificationPreference()
                .setCategory(NotificationCategory.DATA_EXPORT.name()).setInboxEnabled(false).setEmailEnabled(false));
        var preference = service.preference(1L, NotificationCategory.DATA_EXPORT);
        assertTrue(preference.inboxEnabled());
        assertFalse(preference.emailEnabled());
        assertFalse(preference.locked());
    }

    @Test
    void omittedEmailChoicesUseCategoryDefaultsWhenSaving() {
        when(mapper.selectList(any())).thenReturn(List.of());
        service.update(1L, Map.of(NotificationCategory.GENERAL_NOTICE, new Boolean[]{true, null}));
        ArgumentCaptor<UserNotificationPreference> rows = ArgumentCaptor.forClass(UserNotificationPreference.class);
        verify(mapper, times(NotificationCategory.values().length)).insert(rows.capture());
        for (UserNotificationPreference row : rows.getAllValues()) {
            NotificationCategory category = NotificationCategory.valueOf(row.getCategory());
            assertEquals(!EMAIL_OFF.contains(category), row.getEmailEnabled());
        }
    }
}
