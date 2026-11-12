package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMessageMapper extends BaseMapper<UserMessage> {
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))")
    void lockKey(@Param("key") String key);

    @Select("""
            SELECT COUNT(DISTINCT dispatch_id)
            FROM user_messages
            WHERE sender_user_id = #{senderUserId}
              AND created_at >= (
                  date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')
                  AT TIME ZONE 'Asia/Shanghai'
              )
            """)
    int countDailyDispatches(@Param("senderUserId") long senderUserId);
}
