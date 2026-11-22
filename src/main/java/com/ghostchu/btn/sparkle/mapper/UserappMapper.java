package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.entity.Userapp;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface UserappMapper extends BaseMapper<Userapp> {
    Page<Userapp> pageByBanCount(Page<Userapp> page, @Param(Constants.WRAPPER) QueryWrapper<Userapp> filters,
                                @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    int updateUserAppLastThing(long id, String lastUserAgent);

    ApplicationCounts countApplications(@Param("now") OffsetDateTime now);

    record ApplicationCounts(long allTime, long days30, long days14, long days7, long days1) {
    }

    @NotNull
    Cursor<Userapp> fetchAllUserApps();
}
