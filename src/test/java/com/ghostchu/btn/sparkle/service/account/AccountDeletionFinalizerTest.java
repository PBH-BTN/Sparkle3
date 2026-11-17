package com.ghostchu.btn.sparkle.service.account;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserAccountDeletionRequest;
import com.ghostchu.btn.sparkle.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AccountDeletionFinalizerTest {
    @Test
    void removesCareerRowsInIngestionOrder() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        new com.ghostchu.btn.sparkle.autoconfig.MyBatisPlusConfig().configurationCustomizer().customize(configuration);
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "account-deletion-test");
        assistant.setCurrentNamespace("account-deletion-test");
        for (var entity : java.util.List.of(User.class, UserAccountDeletionRequest.class,
                com.ghostchu.btn.sparkle.entity.IpQueryComment.class,
                com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision.class,
                com.ghostchu.btn.sparkle.entity.AiModerationJob.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entity);
        }
        var requests = mock(UserAccountDeletionRequestMapper.class);
        var users = mock(UserMapper.class);
        var career = mock(UserCareerMapper.class);
        var traffic = mock(UserLifetimeTrafficMapper.class);
        var now = OffsetDateTime.now();
        when(requests.selectForUpdate(1L)).thenReturn(new UserAccountDeletionRequest()
                .setStatus("PENDING").setRequestedAt(now.minusDays(10)).setCoolingEndsAt(now.minusDays(1)));
        when(users.selectForUpdate(1L)).thenReturn(new User().setId(1L).setEmail("test@example.test").setNickname("test"));
        var service = new AccountDeletionFinalizer(requests, users, career, traffic,
                mock(UserappMapper.class), mock(UserRelMapper.class), mock(UserContactEmailDeliveryMapper.class),
                mock(UserMessageMapper.class), mock(UserPrivacySettingsMapper.class), mock(IpQueryCommentMapper.class),
                mock(IpQueryCommentRevisionMapper.class), mock(AiModerationJobMapper.class),
                mock(ApplicationEventPublisher.class), mock(AccountDeletionPendingRegistry.class));

        assertTrue(service.finalizeDeletion(1L));

        var order = inOrder(career, traffic);
        order.verify(traffic).deleteCheckpoints(1L);
        order.verify(career).deleteEvents(1L);
        order.verify(career).deleteTime(1L);
        order.verify(traffic).deleteTotals(1L);
        order.verify(career).deleteDaily(1L);
        order.verify(career).deleteTrafficDaily(1L);
    }
}
