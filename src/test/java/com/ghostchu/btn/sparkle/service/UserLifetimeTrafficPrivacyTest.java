package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.entity.UserPrivacySettingsEntity;
import com.ghostchu.btn.sparkle.mapper.UserPrivacySettingsMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserLifetimeTrafficPrivacyTest {
    @Test
    void newUsersDefaultToHiddenAndOldClientsPreserveTheirCurrentPreference() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                UserPrivacySettingsEntity.class);
        assertEquals(LifetimeTrafficVisibility.HIDDEN,
                UserPrivacySettings.defaults(1).lifetimeTrafficVisibility());
        var mapper = mock(UserPrivacySettingsMapper.class);
        var row = new UserPrivacySettingsEntity().setUserId(1L)
                .setAvatarVisibility("BLURRED").setUsernameVisibility("BLURRED")
                .setPointsVisibility("SHOW").setReceiveUserMessages(true)
                .setLifetimeTrafficVisibility("SHOW");
        when(mapper.selectById(1L)).thenReturn(row);
        var service = new UserPrivacyService(mapper);

        assertEquals(LifetimeTrafficVisibility.SHOW, service.update(1,
                AvatarVisibility.HIDDEN, UsernameVisibility.BLURRED, PointsVisibility.HIDDEN)
                .lifetimeTrafficVisibility());
        assertEquals(LifetimeTrafficVisibility.HIDDEN, service.update(1,
                AvatarVisibility.HIDDEN, UsernameVisibility.BLURRED, PointsVisibility.HIDDEN,
                LifetimeTrafficVisibility.HIDDEN).lifetimeTrafficVisibility());
    }
}
