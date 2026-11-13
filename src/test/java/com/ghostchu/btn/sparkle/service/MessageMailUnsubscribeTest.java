package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.service.mail.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MessageMailUnsubscribeTest {
    @Test
    void attachesRecipientScopedLinkToOptionalMail() throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test"),
                UserMessage.class);
        UserMessageMapper mapper = mock(UserMessageMapper.class);
        IUserService users = mock(IUserService.class);
        SmtpMailService smtp = mock(SmtpMailService.class);
        MailSettingsService settings = mock(MailSettingsService.class);
        MailRuntimeSettings runtime = mock(MailRuntimeSettings.class);
        MailUnsubscribeService unsubscribe = mock(MailUnsubscribeService.class);
        MessageMailService service = new MessageMailService(unsubscribe, mapper, users, smtp, settings);
        when(mapper.selectList(any())).thenReturn(List.of(new UserMessage().setId(1L).setRecipientUserId(7L)
                .setSenderKind("SYSTEM").setCategory("CLIENT_SECURITY").setSubject("Subject").setContent("Body")));
        when(users.getById(7L)).thenReturn(new User().setEmail("test@example.com"));
        when(smtp.available()).thenReturn(true);
        when(settings.get()).thenReturn(runtime);
        when(unsubscribe.link(7L, "test@example.com", NotificationCategory.CLIENT_SECURITY)).thenReturn("https://example.com/link");
        service.deliverDispatch(UUID.randomUUID());
        verify(smtp).send(isNull(), eq("test@example.com"), anyString(), anyString(), isNull(), eq("https://example.com/link"));
    }
}
