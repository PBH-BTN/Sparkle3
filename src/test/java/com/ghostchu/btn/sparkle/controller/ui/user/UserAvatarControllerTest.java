package com.ghostchu.btn.sparkle.controller.ui.user;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.BlurredAvatarDiskCache;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.UserPrivacySettings;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAvatarControllerTest {
    @Test
    void blurredAvatarSupportsStrongEtagAndNotModified() throws Exception {
        IUserService userService = mock(IUserService.class);
        BlurredAvatarDiskCache diskCache = mock(BlurredAvatarDiskCache.class);
        UserPrivacyService privacyService = mock(UserPrivacyService.class);
        SparkleUserDetails principal = mock(SparkleUserDetails.class);
        when(principal.getUserId()).thenReturn(1L);

        byte[] blurred = "blurred-avatar".getBytes(StandardCharsets.ISO_8859_1);
        when(diskCache.get(2L, "https://example.com/avatar.jpg")).thenReturn(blurred);
        when(userService.getById(2L)).thenReturn(new User()
                .setId(2L)
                .setAvatar("https://example.com/avatar.jpg"));
        when(privacyService.get(2L)).thenReturn(new UserPrivacySettings(
                2L,
                AvatarVisibility.BLURRED,
                UsernameVisibility.BLURRED,
                PointsVisibility.SHOW,
                true
        ));
        UserAvatarController controller =
                new UserAvatarController(userService, diskCache, privacyService);

        var first = controller.proxyUserAvatar(
                principal,
                2L,
                new ServletWebRequest(new MockHttpServletRequest("GET", "/user/avatar/2"))
        );
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, first.getHeaders().getContentType());
        String etag = first.getHeaders().getETag();
        assertNotNull(etag);
        assertTrue(first.getHeaders().getCacheControl().contains("private"));
        assertTrue(first.getHeaders().getCacheControl().contains("no-cache"));
        assertArrayEquals(blurred, (byte[]) first.getBody());

        MockHttpServletRequest conditionalRequest =
                new MockHttpServletRequest("GET", "/user/avatar/2");
        conditionalRequest.addHeader(HttpHeaders.IF_NONE_MATCH, etag);
        var notModified = controller.proxyUserAvatar(
                principal,
                2L,
                new ServletWebRequest(conditionalRequest)
        );
        assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatusCode());
        assertEquals(etag, notModified.getHeaders().getETag());
    }
}
