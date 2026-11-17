package com.ghostchu.btn.sparkle.service.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ghostchu.btn.sparkle.autoconfig.MyBatisPlusConfig;
import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.AiModerationJobMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.ContentModerationMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.ghostchu.btn.sparkle.service.ai.OpenAiModerationClient.Verdict;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.ghostchu.btn.sparkle.service.moderation.ContentModerationService;
import com.ghostchu.btn.sparkle.service.moderation.UserProfileChangedEvent;
import com.ghostchu.btn.sparkle.service.moderation.UserProfileModerationListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiModerationServiceTest {
    private final AiModerationJobMapper jobMapper =
            mock(AiModerationJobMapper.class);
    private final IpQueryCommentMapper commentMapper =
            mock(IpQueryCommentMapper.class);
    private final IpQueryCommentRevisionMapper revisionMapper =
            mock(IpQueryCommentRevisionMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AiModerationSettingsService settingsService =
            mock(AiModerationSettingsService.class);
    private final OpenAiModerationClient moderationClient =
            mock(OpenAiModerationClient.class);
    private final UserMessageService messageService =
            mock(UserMessageService.class);
    private final ContentModerationMapper dictionaryMapper = mock(ContentModerationMapper.class);
    private final ContentModerationService dictionary = new ContentModerationService(dictionaryMapper);
    private final AiModerationService service = new AiModerationService(
            jobMapper,
            commentMapper,
            revisionMapper,
            userMapper,
            settingsService,
            moderationClient,
            messageService,
            dictionary,
            mock(org.springframework.transaction.PlatformTransactionManager.class)
    );

    @org.junit.jupiter.api.BeforeEach
    void allowConditionalCommentUpdates() {
        when(commentMapper.update(any(), any())).thenReturn(1);
    }

    @BeforeAll
    static void initializeEntityMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        new MyBatisPlusConfig().configurationCustomizer().customize(configuration);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                configuration,
                "ai-moderation-service-test"
        );
        TableInfoHelper.initTableInfo(assistant, AiModerationJob.class);
        TableInfoHelper.initTableInfo(assistant, IpQueryComment.class);
        TableInfoHelper.initTableInfo(assistant, IpQueryCommentRevision.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    @Test
    void terminalFailureUsesTheSameContentBeforeJobOrderAsCompletion() {
        var job = job(7L, 9L, 1L, "RUNNING").setAttemptCount(5).setMaxAttempts(5);

        service.fail(job, new IllegalStateException("unavailable"));

        var order = org.mockito.Mockito.inOrder(commentMapper, jobMapper);
        order.verify(commentMapper).update(any(), any());
        order.verify(jobMapper).update(any(), any());
    }

    @Test
    void avatarIsQueuedEvenWhenNicknameEnqueueFails() {
        when(settingsService.get()).thenReturn(settings(true));
        when(jobMapper.insert(argThat((AiModerationJob job) -> "USERNAME".equals(job.getContentType()))))
                .thenThrow(new IllegalStateException("Nickname enqueue failed"));
        var listener = new UserProfileModerationListener(service);
        String avatar = "https://avatars.githubusercontent.com/u/42?v=4";

        listener.reviewChangedProfile(new UserProfileChangedEvent(7L, 2L, 4L, true, true, "Example", avatar));

        verify(jobMapper).insert(argThat((AiModerationJob job) ->
                "AVATAR".equals(job.getContentType())
                        && Long.valueOf(7L).equals(job.getSubjectUserId())
                        && Long.valueOf(4L).equals(job.getContentVersion())
                        && avatar.equals(job.getImageUrl())
                        && "PENDING".equals(job.getStatus())));
    }

    @Test
    void rejectsPreviouslyQueuedCommentBeforeCallingAi() throws Exception {
        when(dictionaryMapper.read()).thenReturn(new ContentModerationMapper.Dictionary(1, true, "违规词"));
        dictionary.run(null);
        var verdict = service.review(new AiModerationJob()
                .setContentType("COMMENT")
                .setTextContent("评论包含违规词"));
        assertEquals(false, verdict.approved());
        assertEquals(ContentModerationService.CONTENT_REJECTED, verdict.reason());
        verifyNoInteractions(moderationClient);
    }

    @Test
    void completeWritesResultJsonWithTheJsonbAwareStatement() {
        when(settingsService.get()).thenReturn(settings(true));
        AiModerationJob job = job(7L, 9L, 1L, "RUNNING");
        Verdict verdict = new Verdict(
                true,
                "ok",
                List.of(),
                1,
                "{\"approved\":true}"
        );

        service.complete(job, verdict);

        verify(jobMapper).completeRunningJob(
                eq(7L),
                eq("APPROVED"),
                eq("{\"approved\":true}"),
                eq("ok"),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        );
        verify(jobMapper, never()).update(any(), any());
    }

    @Test
    void reauditQueuesPendingCommentThatHasNoJob() {
        when(settingsService.get()).thenReturn(settings(true));
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(3L, 2L, 1, "PENDING")
        ));
        when(jobMapper.selectList(any())).thenReturn(List.of());

        int queued = service.reauditOutstandingComments(50);

        assertEquals(1, queued);
        ArgumentCaptor<AiModerationJob> job =
                ArgumentCaptor.forClass(AiModerationJob.class);
        verify(jobMapper).insert(job.capture());
        assertEquals(3L, job.getValue().getContentRefId());
        assertEquals(2L, job.getValue().getContentVersion());
        assertEquals("PENDING", job.getValue().getStatus());
        assertEquals(0, job.getValue().getAttemptCount());
    }

    @Test
    void reauditSkipsCommentsThatAlreadyHaveAnActiveJob() {
        when(settingsService.get()).thenReturn(settings(true));
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(3L, 2L, 1, "PENDING")
        ));
        when(jobMapper.selectList(any())).thenReturn(List.of(
                job(11L, 3L, 2L, "RUNNING")
        ));

        int queued = service.reauditOutstandingComments(50);

        assertEquals(0, queued);
        verify(jobMapper, never()).insert(any(AiModerationJob.class));
        verify(jobMapper, never()).requeueFailedJob(anyLong(), anyInt());
    }

    @Test
    void reauditRetriesTheFailedJobOfTheCurrentRevision() {
        when(settingsService.get()).thenReturn(settings(true));
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(3L, 2L, 1, "FAILED")
        ));
        when(jobMapper.selectList(any())).thenReturn(List.of(
                job(11L, 3L, 2L, "FAILED")
        ));
        when(jobMapper.requeueFailedJob(11L, 5)).thenReturn(1);

        int queued = service.reauditOutstandingComments(50);

        assertEquals(1, queued);
        verify(jobMapper).requeueFailedJob(11L, 5);
        verify(jobMapper, never()).insert(any(AiModerationJob.class));
    }

    @Test
    void reauditQueuesANewJobWhenTheLatestJobIsOutdated() {
        when(settingsService.get()).thenReturn(settings(true));
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(3L, 4L, 2, "PENDING")
        ));
        when(jobMapper.selectList(any())).thenReturn(List.of(
                job(11L, 3L, 3L, "RUNNING")
        ));

        int queued = service.reauditOutstandingComments(50);

        assertEquals(1, queued);
        ArgumentCaptor<AiModerationJob> job =
                ArgumentCaptor.forClass(AiModerationJob.class);
        verify(jobMapper).insert(job.capture());
        assertEquals(4L, job.getValue().getContentVersion());
    }

    @Test
    void reauditDoesNothingWhenModerationIsDisabled() {
        when(settingsService.get()).thenReturn(settings(false));

        assertEquals(0, service.reauditOutstandingComments(50));
        verifyNoInteractions(commentMapper);
    }

    private AiModerationSettings settings(boolean enabled) {
        return new AiModerationSettings(
                enabled,
                "https://example.test/v1",
                "token",
                "model",
                "rubric",
                30,
                5,
                true
        );
    }

    private AiModerationJob job(
            long id,
            long contentRefId,
            long contentVersion,
            String status
    ) {
        return new AiModerationJob()
                .setId(id)
                .setContentType("COMMENT")
                .setContentRefId(contentRefId)
                .setContentVersion(contentVersion)
                .setStatus(status)
                .setAttemptCount(1)
                .setMaxAttempts(5);
    }

    private IpQueryComment comment(
            long id,
            long generation,
            int revision,
            String status
    ) {
        return new IpQueryComment()
                .setId(id)
                .setNetwork("192.0.2.0/24")
                .setAuthorUserId(5L)
                .setContent("sample comment")
                .setReviewGeneration(generation)
                .setRevision(revision)
                .setModerationStatus(status);
    }
}
