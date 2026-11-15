package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface UserTrafficStatisticsMapper {
    @Select("SELECT COUNT(*) FROM user_traffic_daily WHERE user_id=#{uid}")
    long count(@Param("uid") long uid);

    List<Map<String, String>> daily(@Param("uid") long uid, @Param("offset") long offset,
                                   @Param("size") int size);

    List<Map<String, String>> network(@Param("from") LocalDate from, @Param("through") LocalDate through);
}
