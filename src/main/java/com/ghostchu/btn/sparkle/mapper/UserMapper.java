package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.User;
import org.apache.ibatis.cursor.Cursor;
import org.jetbrains.annotations.NotNull;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.awt.*;
import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM \"user\" WHERE id = #{id} FOR UPDATE")
    User selectForUpdate(@Param("id") long id);

    @NotNull
    Cursor<User> fetchAllUsers();

    @NotNull
    List<Long> fetchAllUserIds();
}
