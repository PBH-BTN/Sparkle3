package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.export.DataExportJob;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DataExportJobMapper {
    String lockAdmission();

    long usedSlots(@Param("uid") long uid, @Param("day") LocalDate day);

    long reservedBytes();

    int enqueue(@Param("job") DataExportJob job, @Param("day") LocalDate day,
                @Param("ip") String ip, @Param("agent") String agent);

    DataExportJob claim();

    int renew(@Param("id") UUID id);

    int ready(@Param("id") UUID id, @Param("rows") int rows, @Param("bytes") long bytes,
              @Param("truncated") boolean truncated, @Param("digest") String digest,
              @Param("expires") OffsetDateTime expires);

    int fail(@Param("id") UUID id, @Param("staleOnly") boolean staleOnly);

    List<DataExportJob> stale();

    List<DataExportJob> removable();

    int removed(@Param("id") UUID id);

    DataExportJob find(@Param("id") UUID id, @Param("uid") long uid);
}
