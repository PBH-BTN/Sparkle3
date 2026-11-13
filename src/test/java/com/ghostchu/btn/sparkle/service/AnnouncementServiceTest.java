package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementInput;
import com.ghostchu.btn.sparkle.entity.Announcement;
import com.ghostchu.btn.sparkle.mapper.AnnouncementMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {
    private final AnnouncementMapper mapper = mock(AnnouncementMapper.class);
    private final UserMessageService messages = mock(UserMessageService.class);
    private final AnnouncementService service = new AnnouncementService(mapper, messages);

    private AnnouncementInput input(boolean enabled, boolean sendInbox) {
        return new AnnouncementInput("BLUE", "GLOBAL_TOP", "公告", "", "正文", "",
                enabled, true, 0, null, null, sendInbox);
    }

    private Announcement existing(boolean enabled) {
        Announcement announcement = new Announcement().setId(1L).setLevel("BLUE")
                .setPlacement("GLOBAL_TOP").setTitleZh("公告").setTitleEn("")
                .setContentZh("正文").setContentEn("").setEnabled(enabled);
        when(mapper.selectById(1L)).thenReturn(announcement);
        return announcement;
    }

    private void prepareCreate(boolean enabled) {
        existing(enabled);
        doAnswer(invocation -> {
            ((Announcement) invocation.getArgument(0)).setId(1L);
            return 1;
        }).when(mapper).insert(any(Announcement.class));
    }

    @Test
    void creatingPublishedAnnouncementDoesNotSendInboxByDefault() throws Exception {
        prepareCreate(true);
        service.create(input(true, false), 2L);
        verifyNoInteractions(messages);
    }

    @Test
    void publishingExistingAnnouncementDoesNotSendInboxByDefault() throws Exception {
        existing(false);
        service.update(1L, input(true, false), 2L);
        verifyNoInteractions(messages);
    }

    @Test
    void explicitlyRequestedInboxIsSentWhenCreatingPublishedAnnouncement() throws Exception {
        prepareCreate(true);
        service.create(input(true, true), 2L);
        verify(messages).publishSystem(NotificationCategory.DAILY_ANNOUNCEMENT, "公告", "正文", true);
    }

    @Test
    void explicitlyRequestedInboxIsSentWhenSavingPublishedAnnouncement() throws Exception {
        existing(true);
        service.update(1L, input(true, true), 2L);
        verify(messages).publishSystem(NotificationCategory.DAILY_ANNOUNCEMENT, "公告", "正文", true);
    }

    @Test
    void disabledAnnouncementDoesNotSendInboxEvenIfRequested() throws Exception {
        prepareCreate(false);
        service.create(input(false, true), 2L);
        service.update(1L, input(false, true), 2L);
        verifyNoInteractions(messages);
    }
}
