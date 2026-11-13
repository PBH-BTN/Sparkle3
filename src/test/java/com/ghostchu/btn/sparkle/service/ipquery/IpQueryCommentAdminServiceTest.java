package com.ghostchu.btn.sparkle.service.ipquery;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.autoconfig.MyBatisPlusConfig;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentDetail;
import com.ghostchu.btn.sparkle.controller.admin.dto.IpQueryCommentAdminModels.AdminCommentUpdateRequest;
import com.ghostchu.btn.sparkle.entity.AiModerationJob;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AiModerationJobMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpQueryCommentAdminServiceTest {
    private final IpQueryCommentMapper commentMapper =
            mock(IpQueryCommentMapper.class);
    private final IpQueryCommentRevisionMapper revisionMapper =
            mock(IpQueryCommentRevisionMapper.class);
    private final AiModerationJobMapper jobMapper =
            mock(AiModerationJobMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final IpQuerySettingsService settingsService =
            mock(IpQuerySettingsService.class);
    private final AiModerationService moderationService =
            mock(AiModerationService.class);
    private final IpQueryCommentAdminService service =
            new IpQueryCommentAdminService(
                    commentMapper,
                    revisionMapper,
                    jobMapper,
                    userService,
                    settingsService,
                    moderationService
            );

    @BeforeAll
    static void initializeEntityMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        new MyBatisPlusConfig().configurationCustomizer().customize(configuration);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                configuration,
                "comment-admin-service-test"
        );
        TableInfoHelper.initTableInfo(assistant, IpQueryComment.class);
        TableInfoHelper.initTableInfo(assistant, IpQueryCommentRevision.class);
    }

    @BeforeEach
    void stubSettings() {
        when(settingsService.get()).thenReturn(
                new IpQuerySettingsService.Settings(true, true, 5_000, 30)
        );
    }

    @Test
    void normalizeScopeMasksHostBitsAndRejectsInvalidInput() throws Exception {
        assertEquals("192.0.2.0/24", service.normalizeScope("192.0.2.42/24"));
        assertEquals("2001:db8::1", service.normalizeScope("2001:db8::1"));
        assertEquals("2001:db8::/32", service.normalizeScope("2001:db8::1/32"));
        assertEquals(null, service.normalizeScope("   "));
        assertThrows(
                BusinessException.class,
                () -> service.normalizeScope("not-an-ip")
        );
    }

    @Test
    void listMapsAuthorAndLatestReviewJob() throws Exception {
        IpQueryComment comment = comment();
        Page<IpQueryComment> page = new Page<>(1, 20);
        page.setRecords(List.of(comment));
        page.setTotal(1);
        when(commentMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.listByIds(anyList())).thenReturn(List.of(
                new User().setId(5L).setNickname("alice")
        ));
        when(jobMapper.selectList(any())).thenReturn(List.of(
                new AiModerationJob()
                        .setContentRefId(comment.getId())
                        .setStatus("FAILED")
        ));

        var result = service.list(1, 20, "", "192.0.2.42/24", "", false);

        assertEquals(1, result.total());
        assertEquals(1, result.records().size());
        assertEquals("alice", result.records().getFirst().authorNickname());
        assertEquals("5", result.records().getFirst().authorUserId());
        assertEquals("FAILED", result.records().getFirst().latestJobStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<IpQueryComment>> query =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(commentMapper).selectPage(any(), query.capture());
        assertTrue(
                query.getValue().getSqlSegment().contains("network <<= CAST("),
                "the list must filter by the normalized network scope"
        );
    }

    @Test
    void updateCreatesRevisionAndApprovesEditedContent() throws Exception {
        IpQueryComment existing = comment()
                .setContent("old")
                .setRevision(2)
                .setModerationStatus("PENDING")
                .setReviewGeneration(3L);
        IpQueryComment updated = comment()
                .setContent("new\ncontent")
                .setRevision(3)
                .setModerationStatus("APPROVED")
                .setReviewGeneration(4L);
        when(commentMapper.selectById(9L)).thenReturn(existing, updated);
        when(commentMapper.update(any(), any())).thenReturn(1);
        when(revisionMapper.selectList(any())).thenReturn(List.of());
        when(jobMapper.selectList(any())).thenReturn(List.of());

        AdminCommentDetail detail = service.update(
                42L,
                9L,
                new AdminCommentUpdateRequest(2, "  new \r\ncontent ", null, null),
                "192.0.2.1",
                "JUnit"
        );

        assertEquals("APPROVED", detail.comment().moderationStatus());
        ArgumentCaptor<IpQueryCommentRevision> revision =
                ArgumentCaptor.forClass(IpQueryCommentRevision.class);
        verify(revisionMapper).insert(revision.capture());
        assertEquals(3, revision.getValue().getRevision());
        assertEquals("new \ncontent", revision.getValue().getContent());
        assertEquals(42L, revision.getValue().getEditorUserId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<IpQueryComment>> update =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(commentMapper).update(isNull(), update.capture());
        assertTrue(
                update.getValue().getSqlSet().contains("review_generation"),
                "an admin edit must advance the review generation"
        );
        verify(moderationService, never()).enqueueComment(any());
    }

    @Test
    void updateRejectsStaleRevisions() {
        when(commentMapper.selectById(9L)).thenReturn(comment().setRevision(2));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.update(
                        42L,
                        9L,
                        new AdminCommentUpdateRequest(1, "new", null, null),
                        null,
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(commentMapper, never()).update(any(), any());
    }

    @Test
    void updateCannotQueueReviewWhenAiModerationIsDisabled() {
        when(commentMapper.selectById(9L)).thenReturn(comment()
                .setRevision(1)
                .setModerationStatus("APPROVED"));
        when(moderationService.enabled()).thenReturn(false);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.update(
                        42L,
                        9L,
                        new AdminCommentUpdateRequest(1, null, "PENDING", null),
                        null,
                        null
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(commentMapper, never()).update(any(), any());
    }

    private IpQueryComment comment() {
        return new IpQueryComment()
                .setId(9L)
                .setNetwork("192.0.2.0/24")
                .setAuthorUserId(5L)
                .setContent("sample comment")
                .setRevision(1)
                .setReviewGeneration(1L)
                .setModerationStatus("PENDING");
    }
}
