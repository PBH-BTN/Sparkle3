package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.SwarmTrafficDelta;
import org.jetbrains.annotations.NotNull;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SwarmTrafficDeltaMapper extends BaseMapper<SwarmTrafficDelta> {
    void batchInsert(@Param("rows") @NotNull List<SwarmTrafficDelta> rows);
}
