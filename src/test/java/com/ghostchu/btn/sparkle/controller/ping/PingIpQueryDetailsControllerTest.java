package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentAccess;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentView;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CreateCommentRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.UpdateCommentRequest;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.btn.BtnRouteResolver;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentService;
import com.ghostchu.btn.sparkle.util.LazyLoad;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PingIpQueryDetailsControllerTest {
    private static final String CLIENT_IP = "192.0.2.10";
    private static final String SCOPE = "192.0.2.0/24";
    private static final String AVATAR_PATH = "/user/avatar/42";
    private final BtnRuntimeConfigService config = mock(BtnRuntimeConfigService.class);
    private final IUserService users = mock(IUserService.class);
    private final IpQueryCommentService comments = mock(IpQueryCommentService.class);
    private final IPDBManager ipdb = mock(IPDBManager.class);
    private final PingIpQueryDetailsController controller = spy(new PingIpQueryDetailsController(
            config, users, null, comments, null, new BtnRouteResolver(config, ipdb)));

    @BeforeEach
    void setUp() throws Exception {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT_IP);
        request.addHeader("User-Agent", "test");
        controller.request = request;
        doReturn(new Userapp().setOwner(42L).setAppId("test-app"))
                .when(controller).verifyUserApplicationForBatchedPoints();
        when(users.getById(42L)).thenReturn(new User());
        when(config.text("rootUrls", "rootUrl")).thenReturn("https://global.example/btn/");
    }

    @Test
    void listMakesVisibleAvatarsAbsoluteAndPreservesHiddenAvatars() throws Exception {
        var access = new CommentAccess(true, true, true, 5000, 30);
        var visible = comment(1L, AVATAR_PATH);
        var hidden = comment(2L, null);
        when(comments.list(42L, SCOPE, 1, 20, false))
                .thenReturn(new CommentPage(List.of(visible, hidden), 1, 20, 2, 1, SCOPE, access));

        var page = controller.comments(SCOPE, 1, 20);

        assertEquals(comment(1L, "https://global.example/btn" + AVATAR_PATH), page.records().getFirst());
        assertEquals(hidden, page.records().getLast());
        assertEquals(access, page.access());
        verifyNoInteractions(ipdb);
    }

    @Test
    void createAndUpdateMakeAvatarsAbsolute() throws Exception {
        var create = new CreateCommentRequest(SCOPE, null, "test");
        var update = new UpdateCommentRequest("updated", 1);
        var visible = comment(1L, AVATAR_PATH);
        when(comments.create(42L, create, CLIENT_IP, "test")).thenReturn(visible);
        when(comments.update(42L, 1L, update, CLIENT_IP, "test")).thenReturn(visible);

        var geoData = new IPGeoData();
        var city = new IPGeoData.CityData();
        city.setCnProvince("江苏省");
        geoData.setCity(city);
        when(config.booleanValue("rootUrls", "autoRouteEnabled")).thenReturn(true);
        when(ipdb.queryIPDB(InetAddress.ofLiteral(CLIENT_IP)))
                .thenReturn(new IPDBManager.IPDBResponse(new LazyLoad<>(() -> geoData)));
        when(config.routeUrl(null, "江苏省")).thenReturn("https://optimized.example");

        assertEquals("https://optimized.example" + AVATAR_PATH, controller.create(create).authorAvatarUrl());
        assertEquals("https://optimized.example" + AVATAR_PATH, controller.update(1L, update).authorAvatarUrl());
    }

    private CommentView comment(long id, String avatarUrl) {
        return new CommentView(id, null, SCOPE, "42", "author", avatarUrl, false, false, "test",
                1, false, "APPROVED", null, null, null, null, null, true, true, true, List.of());
    }
}
