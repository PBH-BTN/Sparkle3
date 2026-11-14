package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface AfdianEmailBindingMapper {
    @Select("""
            SELECT EXISTS (SELECT 1 FROM afdian_bindings WHERE afdian_user_id = #{externalId})
                OR EXISTS (SELECT 1 FROM member_platform_bindings
                    WHERE provider = 'AFDIAN' AND external_user_id = #{externalId})
            """)
    boolean hasExternalBinding(@Param("externalId") String externalId);

    @Select("""
            SELECT EXISTS (SELECT 1 FROM afdian_bindings WHERE btn_user_id = #{userId})
                OR EXISTS (SELECT 1 FROM member_platform_bindings
                    WHERE provider = 'AFDIAN' AND btn_user_id = #{userId})
            """)
    boolean hasUserBinding(@Param("userId") long userId);

    @Select("SELECT id, email, banned_at FROM \"user\" WHERE lower(email) = #{email} LIMIT 2")
    List<User> findUsers(@Param("email") String email);

    @Insert("""
            INSERT INTO afdian_bindings (btn_user_id, afdian_user_id, bound_order_no)
            VALUES (#{userId}, #{externalId}, #{orderId}) ON CONFLICT DO NOTHING
            """)
    int insertLegacy(@Param("userId") long userId, @Param("externalId") String externalId,
                     @Param("orderId") String orderId);

    @Insert("""
            INSERT INTO member_platform_bindings (provider, btn_user_id, external_user_id, proof, last_synced_at)
            VALUES ('AFDIAN', #{userId}, #{externalId}, #{orderId}, now()) ON CONFLICT DO NOTHING
            """)
    int insertMember(@Param("userId") long userId, @Param("externalId") String externalId,
                     @Param("orderId") String orderId);
}
