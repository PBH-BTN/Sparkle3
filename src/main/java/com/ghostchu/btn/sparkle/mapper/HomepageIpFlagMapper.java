package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HomepageIpFlagMapper {
    void observe(@Param("day") LocalDate day, @Param("rows") List<Observation> rows);

    int publish(@Param("limit") int limit, @Param("metricCount") int metricCount);

    boolean hasOlderPending(@Param("before") LocalDate before);

    List<String> prune(@Param("before") LocalDate before);

    List<DailyCount> daily(@Param("from") LocalDate from, @Param("to") LocalDate to);

    record Observation(String ip, int mask) { }

    record DailyCount(LocalDate day, int metric, long count) { }
}
