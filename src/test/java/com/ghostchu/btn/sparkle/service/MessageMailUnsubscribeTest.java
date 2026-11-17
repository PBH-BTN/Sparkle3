package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import com.ghostchu.btn.sparkle.mapper.UserMessageMapper;
import com.ghostchu.btn.sparkle.service.mail.*;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MessageMailUnsubscribeTest {
    @ParameterizedTest
    @EnumSource(value = NotificationCategory.class, names = {"CLIENT_SECURITY", "DATA_EXPORT"})
    void attachesRecipientScopedLinkToOptionalMail(NotificationCategory category) throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test"),
                UserMessage.class);
        UserMessageMapper mapper = mock(UserMessageMapper.class);
        IUserService users = mock(IUserService.class);
        SmtpMailService smtp = mock(SmtpMailService.class);
        MailSettingsService settings = mock(MailSettingsService.class);
        MailRuntimeSettings runtime = mock(MailRuntimeSettings.class);
        MailUnsubscribeService unsubscribe = mock(MailUnsubscribeService.class);
        var config = mock(BtnRuntimeConfigService.class);
        when(config.text("rootUrls", "rootUrl")).thenReturn("https://example.com");
        MessageMailService service = new MessageMailService(unsubscribe, mapper, users, smtp, settings, config);
        String content = category == NotificationCategory.DATA_EXPORT
                ? "Body\n/api/ui/exports/00000000-0000-0000-0000-000000000001/download?token=fixture" : "Body";
        when(mapper.selectList(any())).thenReturn(List.of(new UserMessage().setId(1L).setRecipientUserId(7L)
                .setSenderKind("SYSTEM").setCategory(category.name()).setSubject("Subject").setContent(content)));
        when(users.getById(7L)).thenReturn(new User().setEmail("test@example.com"));
        when(smtp.available()).thenReturn(true);
        when(settings.get()).thenReturn(runtime);
        when(unsubscribe.link(7L, "test@example.com", category)).thenReturn("https://example.com/link");
        service.deliverDispatch(UUID.randomUUID());
        verify(smtp).send(isNull(), eq("test@example.com"), anyString(),
                argThat(body -> body.contains("Body") && !body.contains("token=fixture")
                        && (category != NotificationCategory.DATA_EXPORT || body.contains("https://example.com/user/inbox"))),
                isNull(), eq("https://example.com/link"));
    }
}
