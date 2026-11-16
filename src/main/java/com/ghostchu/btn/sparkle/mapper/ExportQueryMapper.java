package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.Map;

public interface ExportQueryMapper {
    Cursor<Map<String, Object>> bans(@Param(Constants.WRAPPER) QueryWrapper<?> query, @Param("limit") long limit);

    Cursor<Map<String, Object>> clients(@Param(Constants.WRAPPER) QueryWrapper<?> query, @Param("limit") long limit);

    Cursor<Map<String, Object>> swarm(@Param(Constants.WRAPPER) QueryWrapper<?> query, @Param("limit") long limit);
}
