package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface CareerBatchMapper {
    int bans(@Param("uid") long uid, @Param("rows") List<Map<String,Object>> rows, @Param("heartbeatWindowMillis") long heartbeatWindowMillis);
    int features(@Param("uid") long uid, @Param("rows") List<Map<String,Object>> rows);
    int swarm(@Param("uid") long uid, @Param("rows") List<Map<String,Object>> rows, @Param("activityWindowMillis") long activityWindowMillis, @Param("heartbeatWindowMillis") long heartbeatWindowMillis);
}
