package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface CareerBatchMapper {
    List<DailyTrafficDelta> bans(@Param("uid") long uid, @Param("rows") List<Map<String,Object>> rows, @Param("heartbeatWindowMillis") long heartbeatWindowMillis);
    List<CareerDailyDelta> features(@Param("uid") long uid, @Param("rows") List<Map<String,Object>> rows);
    List<DailyTrafficDelta> swarm(@Param("uid") long uid, @Param("rows") List<Map<String,Object>> rows, @Param("activityWindowMillis") long activityWindowMillis, @Param("heartbeatWindowMillis") long heartbeatWindowMillis);
    int upsertDailyTraffic(@Param("rows") List<DailyTrafficDelta> rows);

    int upsertLifetimeTraffic(@Param("rows") List<DailyTrafficDelta> rows);
    int upsertCareerDaily(@Param("rows") List<CareerDailyDelta> rows);

    record CareerDailyDelta(long userId, LocalDate day, String kind, long amount) { }

    record DailyTrafficDelta(long userId, LocalDate day, BigDecimal upload, BigDecimal download,
                             BigDecimal uploadAck, BigDecimal downloadAck, BigDecimal loss,
                             BigDecimal seeding, BigDecimal downloading,
                             long swarmEvents, long banEvents, long observationEvents) {
        public DailyTrafficDelta(long userId, LocalDate day, BigDecimal upload, BigDecimal download,
                                 BigDecimal uploadAck, BigDecimal downloadAck, BigDecimal loss,
                                 BigDecimal seeding, BigDecimal downloading) {
            this(userId, day, upload, download, uploadAck, downloadAck, loss, seeding, downloading, 0, 0, 0);
        }
    }
}
