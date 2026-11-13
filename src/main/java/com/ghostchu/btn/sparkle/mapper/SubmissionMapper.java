package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.SubmissionModels.SubmissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface SubmissionMapper {
    List<Long> findApplicationIds(@Param("owner") long owner);

    IPage<SubmissionRecord> query(IPage<SubmissionRecord> page, @Param("owner") long owner,
                                  @Param("applicationIds") List<Long> applicationIds,
                                  @Param("type") String type, @Param("start") OffsetDateTime start,
                                  @Param("end") OffsetDateTime end);
}
