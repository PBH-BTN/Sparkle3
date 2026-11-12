package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserContactEmailDelivery;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserContactEmailDeliveryMapper
        extends BaseMapper<UserContactEmailDelivery> {
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))")
    void lockKey(@Param("key") String key);

    @Select("""
            SELECT COUNT(*)
            FROM user_contact_email_deliveries
            WHERE sender_user_id = #{senderUserId}
              AND sent_at >= (
                  date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')
                  AT TIME ZONE 'Asia/Shanghai'
              )
            """)
    int countToday(@Param("senderUserId") long senderUserId);
}
