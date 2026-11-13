package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.SubmissionModels.SubmissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.OffsetDateTime;

@Mapper
public interface SubmissionMapper {
    IPage<SubmissionRecord> query(IPage<SubmissionRecord> page, @Param("owner") long owner,
                                  @Param("type") String type, @Param("start") OffsetDateTime start,
                                  @Param("end") OffsetDateTime end);
}
