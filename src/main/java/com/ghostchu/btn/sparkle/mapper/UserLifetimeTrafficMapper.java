package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.LifetimeTraffic;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserLifetimeTrafficMapper {
    @Select("""
            SELECT COALESCE(sent_self,0)::text AS sent_traffic_self_report,
                   COALESCE(received_self,0)::text AS received_traffic_self_report,
                   COALESCE(sent_ack,0)::text AS sent_traffic_other_ack,
                   COALESCE(received_ack,0)::text AS received_traffic_other_ack,
                   COALESCE(tracked_since,u.register_at) AS tracked_since,
                   COALESCE(history_partial,FALSE) AS history_partial,
                   COALESCE(updated_at,u.register_at) AS updated_at
            FROM "user" u LEFT JOIN user_lifetime_traffic t ON t.user_id=u.id WHERE u.id = #{userId}
            """)
    LifetimeTraffic selectByUserId(@Param("userId") long userId);

    @Delete("DELETE FROM user_lifetime_traffic_checkpoint WHERE user_id = #{userId}")
    void deleteCheckpoints(@Param("userId") long userId);

    @Delete("DELETE FROM user_lifetime_traffic WHERE user_id = #{userId}")
    void deleteTotals(@Param("userId") long userId);
}
