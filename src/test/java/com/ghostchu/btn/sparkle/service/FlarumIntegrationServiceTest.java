package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuntimeConfigView;
import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.UserDataResponse;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlarumIntegrationServiceTest {
    private final UserRelMapper userRelMapper = mock(UserRelMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final IUserSwarmStatisticsService swarmStatisticsService =
            mock(IUserSwarmStatisticsService.class);
    private final AdminConfigService adminConfigService = mock(AdminConfigService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final FlarumIntegrationService service = new FlarumIntegrationService(
            userRelMapper,
            userService,
            swarmStatisticsService,
            adminConfigService,
            jdbcTemplate
    );

    @Test
    void rejectsInvalidGithubUid() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.findByGithubUid("not-a-number")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(userRelMapper);
    }

    @Test
    void returnsUnboundResponseWhenGithubUidHasNoRelation() throws BusinessException {
        when(userRelMapper.findUserRelByPlatformUserId("github", "12345"))
                .thenReturn(null);

        UserDataResponse response = service.findByGithubUid("12345");

        assertFalse(response.bound());
        assertEquals("12345", response.githubUid());
        assertEquals(null, response.btnUserId());
    }

    @Test
    void returnsBoundUserWithPointsRank() throws BusinessException {
        UserRel relation = new UserRel().setBindUserId(42L);
        User user = new User()
                .setId(42L)
                .setNickname("sparkle-user")
                .setScoreBytes(4096L);

        when(userRelMapper.findUserRelByPlatformUserId("github", "12345"))
                .thenReturn(relation);
        when(userService.getById(42L)).thenReturn(user);
        when(userService.getSystemUids()).thenReturn(List.of());
        when(adminConfigService.get()).thenReturn(new RuntimeConfigView(
                1,
                10,
                false,
                false,
                false,
                60,
                7,
                "{{title}}",
                "{{content}}"
        ));
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(9L);

        UserDataResponse response = service.findByGithubUid("12345");

        assertTrue(response.bound());
        assertEquals("42", response.btnUserId());
        assertEquals("sparkle-user", response.basicInfo().nickname());
        assertEquals("4096", response.points().balanceBytes());
        assertEquals(9L, response.rankings().points().rank());
        assertFalse(response.traffic7d().enabled());
    }
}
