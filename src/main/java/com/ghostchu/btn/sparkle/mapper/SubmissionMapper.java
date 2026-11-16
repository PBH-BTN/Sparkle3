package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.SubmissionModels.SubmissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SubmissionMapper {
    List<Long> findApplicationIds(@Param("owner") long owner);

    Cursor<Map<String, Object>> exportRows(@Param("owner") long owner, @Param("applicationIds") List<Long> ids,
                                            @Param("type") String type, @Param("start") OffsetDateTime start,
                                            @Param("end") OffsetDateTime end, @Param("limit") long limit);

    IPage<SubmissionRecord> query(IPage<SubmissionRecord> page, @Param("owner") long owner,
                                  @Param("applicationIds") List<Long> applicationIds,
                                  @Param("type") String type, @Param("start") OffsetDateTime start,
                                  @Param("end") OffsetDateTime end);
}
