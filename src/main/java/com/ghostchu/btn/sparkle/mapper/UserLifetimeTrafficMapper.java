package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.LifetimeTraffic;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserLifetimeTrafficMapper {
    @Select("""
            SELECT sent_self::text AS sent_traffic_self_report,
                   received_self::text AS received_traffic_self_report,
                   sent_ack::text AS sent_traffic_other_ack,
                   received_ack::text AS received_traffic_other_ack,
                   tracked_since, history_partial, updated_at
            FROM user_lifetime_traffic WHERE user_id = #{userId}
            """)
    LifetimeTraffic selectByUserId(@Param("userId") long userId);

    @Delete("DELETE FROM user_lifetime_traffic_checkpoint WHERE user_id = #{userId}")
    void deleteCheckpoints(@Param("userId") long userId);

    @Delete("DELETE FROM user_lifetime_traffic WHERE user_id = #{userId}")
    void deleteTotals(@Param("userId") long userId);
}
