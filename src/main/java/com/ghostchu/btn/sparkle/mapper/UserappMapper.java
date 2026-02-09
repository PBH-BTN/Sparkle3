package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import org.apache.ibatis.cursor.Cursor;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface UserappMapper extends BaseMapper<Userapp> {
    int updateUserAppLastThing(long id, String lastUserAgent);

    @NotNull
    Cursor<Userapp> fetchAllUserApps();
}
