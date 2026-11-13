package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserMessageConversationTest {
    private final UserMessageMapper mapper = mock(UserMessageMapper.class);
    private final UserMessageService service = new UserMessageService(mapper, mock(IUserService.class),
            mock(UserPublicProfileService.class), mock(NotificationPreferenceService.class),
            mock(MessageMailService.class));

    private UserMessage message() {
        return new UserMessage().setId(10L).setSenderUserId(1L).setRecipientUserId(2L)
                .setSenderKind("USER").setCategory("DIRECT_MESSAGE").setBroadcast(false)
                .setInboxVisible(true).setSenderDisplayName("Sender");
    }

    @Test
    void rejectsMessagesOutsideParticipants() {
        when(mapper.selectById(10L)).thenReturn(message());
        assertThrows(BusinessException.class, () -> service.conversation(3L, 10L, 1, 20));
        verify(mapper, never()).selectPage(any(), any());
    }

    @Test
    void senderCanReadSentMessagesEvenAfterRecipientDeletesThem() throws Exception {
        UserMessage sent = message().setDeletedAt(java.time.OffsetDateTime.now());
        when(mapper.selectById(10L)).thenReturn(sent);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<UserMessage> page = invocation.getArgument(0);
            return page.setRecords(List.of(sent)).setTotal(1);
        });
        var result = service.conversation(1L, 10L, 0, 500);
        assertTrue(result.records().getFirst().outgoing());
        assertTrue(result.records().getFirst().read());
        assertEquals("2", result.records().getFirst().peerUserId());
        assertEquals(100, result.size());
        assertEquals(1, result.current());
        ArgumentCaptor<Wrapper<UserMessage>> query = ArgumentCaptor.captor();
        verify(mapper).selectPage(any(), query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("sender_user_id"));
        assertTrue(sql.contains("recipient_user_id"));
        assertTrue(sql.contains("deleted_at IS NULL"));
        assertTrue(sql.contains("inbox_visible"));
    }

    @Test
    void recipientCannotReadDeletedMessage() {
        when(mapper.selectById(10L)).thenReturn(message().setDeletedAt(java.time.OffsetDateTime.now()));
        assertThrows(BusinessException.class, () -> service.conversation(2L, 10L, 1, 20));
    }

    @Test
    void systemIdentityDoesNotExposeStaffSentHistory() {
        when(mapper.selectById(10L)).thenReturn(message().setSenderKind("SYSTEM"));
        assertThrows(BusinessException.class, () -> service.conversation(1L, 10L, 1, 20));
    }

    @Test
    void unreadConversationRemainsUnreadWhenLatestMessageIsOutgoing() {
        when(mapper.selectConversations(any(), eq(1L), eq(true))).thenAnswer(invocation -> {
            Page<UserMessage> page = invocation.getArgument(0);
            return page.setRecords(List.of(message().setHasUnread(true))).setTotal(1);
        });
        var result = service.inbox(1L, 1, 20, true);
        assertTrue(result.records().getFirst().outgoing());
        assertFalse(result.records().getFirst().read());
    }
}
