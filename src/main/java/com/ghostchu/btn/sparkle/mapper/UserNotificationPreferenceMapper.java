package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserNotificationPreference;

public interface UserNotificationPreferenceMapper
        extends BaseMapper<UserNotificationPreference> {
    void disableEmail(@org.apache.ibatis.annotations.Param("userId") long userId,
                      @org.apache.ibatis.annotations.Param("category") String category);

}
