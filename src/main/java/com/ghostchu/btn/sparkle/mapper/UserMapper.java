package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.User;
import org.apache.ibatis.cursor.Cursor;
import org.jetbrains.annotations.NotNull;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.ArrayTypeHandler;
import org.apache.ibatis.type.JdbcType;

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
    // Profile/role changes do not modify the primary key. Keep foreign-key checks compatible.
    @Select("SELECT * FROM \"user\" WHERE id = #{id} FOR NO KEY UPDATE")
    @Results(id = "userWithRoles", value = @Result(column = "roles", property = "roles",
            javaType = String[].class, jdbcType = JdbcType.ARRAY, typeHandler = ArrayTypeHandler.class))
    User selectForUpdate(@Param("id") long id);

    @Update("""
            UPDATE "user"
            SET roles = #{roles,jdbcType=ARRAY,typeHandler=org.apache.ibatis.type.ArrayTypeHandler},
                supporter_revoked_at = CASE
                    WHEN 'supporter' = ANY(roles)
                        AND NOT ('supporter' = ANY(#{roles,jdbcType=ARRAY,typeHandler=org.apache.ibatis.type.ArrayTypeHandler}))
                    THEN COALESCE(supporter_revoked_at, now())
                    ELSE supporter_revoked_at
                END
            WHERE id = #{id}
            """)
    int replaceRoles(@Param("id") long id, @Param("roles") String[] roles);

    @Update("""
            UPDATE "user"
            SET roles = ARRAY['user'], supporter_revoked_at = COALESCE(supporter_revoked_at, now())
            WHERE id = #{id}
            """)
    int clearRolesForDeletedUser(@Param("id") long id);

    @NotNull
    Cursor<User> fetchAllUsers();

    @NotNull
    List<Long> fetchAllUserIds();
}
