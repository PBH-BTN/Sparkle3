package com.ghostchu.btn.sparkle.service.ipquery;

import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentAccess;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentView;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import com.ghostchu.btn.sparkle.entity.IpQueryCommentRevision;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentMapper;
import com.ghostchu.btn.sparkle.mapper.IpQueryCommentRevisionMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpQueryCommentServicePrivacyTest {
    private final IpQueryCommentMapper commentMapper =
            mock(IpQueryCommentMapper.class);
    private final IpQueryCommentRevisionMapper revisionMapper =
            mock(IpQueryCommentRevisionMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final UserPrivacyService privacyService =
            mock(UserPrivacyService.class);
    private final IpQuerySettingsService settingsService =
            mock(IpQuerySettingsService.class);
    private final IpQueryLookupService lookupService =
            mock(IpQueryLookupService.class);
    private final AiModerationService moderationService =
            mock(AiModerationService.class);
    private final IpQueryCommentService service = new IpQueryCommentService(
            commentMapper,
            revisionMapper,
            userService,
            privacyService,
            settingsService,
            lookupService,
            moderationService
    );

    @Test
    void regularViewersNeverReceivePostingIpOrUserAgent() throws Exception {
        CommentPage page = loadAs(new User().setId(7L).setNickname("viewer"));

        CommentView comment = page.records().getFirst();
        assertNull(comment.authorIp());
        assertNull(comment.authorUserAgent());
        assertNull(comment.history().getFirst().editorIp());
        assertNull(comment.history().getFirst().editorUserAgent());
    }

    @Test
    void moderatorsReceivePostingIpAndUserAgent() throws Exception {
        CommentPage page = loadAs(new User()
                .setId(8L)
                .setNickname("moderator")
                .setRole("moderator"));

        CommentView comment = page.records().getFirst();
        assertEquals("192.0.2.10", comment.authorIp());
        assertEquals("curl/8", comment.authorUserAgent());
        assertEquals("192.0.2.11", comment.history().getFirst().editorIp());
        assertEquals("curl/8", comment.history().getFirst().editorUserAgent());
    }

    @Test
    void administratorsReceivePostingIpAndUserAgent() throws Exception {
        CommentPage page = loadAs(new User()
                .setId(9L)
                .setNickname("admin")
                .setRole("admin"));

        assertEquals("192.0.2.10", page.records().getFirst().authorIp());
    }

    @Test
    void authorsDoNotReceiveTheirOwnPostingIp() throws Exception {
        CommentPage page = loadAs(new User().setId(5L).setNickname("author"));

        CommentView comment = page.records().getFirst();
        assertTrue(comment.self());
        assertNull(comment.authorIp());
        assertNull(comment.authorUserAgent());
    }

    private CommentPage loadAs(User viewer) throws Exception {
        User author = new User()
                .setId(5L)
                .setNickname("author")
                .setRole("user");
        IpQueryComment comment = new IpQueryComment()
                .setId(9L)
                .setNetwork("192.0.2.0/24")
                .setAuthorUserId(5L)
                .setContent("sample comment")
                .setRevision(1)
                .setReviewGeneration(0L)
                .setModerationStatus("APPROVED")
                .setAuthorIp(InetAddress.ofLiteral("192.0.2.10"))
                .setAuthorUserAgent("curl/8")
                .setCreatedAt(OffsetDateTime.now());
        IpQueryCommentRevision revision = new IpQueryCommentRevision()
                .setCommentId(9L)
                .setRevision(1)
                .setContent("sample comment")
                .setModerationStatus("APPROVED")
                .setEditorUserId(5L)
                .setEditorIp(InetAddress.ofLiteral("192.0.2.11"))
                .setEditorUserAgent("curl/8")
                .setCreatedAt(OffsetDateTime.now());
        when(lookupService.access()).thenReturn(
                new CommentAccess(true, true, false, 5_000, 30)
        );
        when(commentMapper.countVisible(anyString(), anyLong())).thenReturn(1L);
        when(commentMapper.selectVisiblePage(
                anyString(),
                anyLong(),
                anyInt(),
                anyLong()
        )).thenReturn(List.of(comment));
        when(userService.listByIds(anyList())).thenReturn(List.of(author));
        when(privacyService.getAll(anyList())).thenReturn(Map.of());
        when(revisionMapper.selectList(any())).thenReturn(List.of(revision));
        when(userService.getById(viewer.getId())).thenReturn(viewer);
        return service.list(viewer.getId(), "192.0.2.0/24", 1, 20);
    }
}
