package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.AnalyseRule;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseByModuleResult;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseConcurrentDownloadResult;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseOverDownloadedResult;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface AnalyseRuleMapper extends BaseMapper<AnalyseRule> {
    @NotNull
    List<AnalyseByModuleResult> analyseByModule(@NotNull OffsetDateTime afterTimestamp, @NotNull List<String> moduleName);

    @NotNull
    List<AnalyseOverDownloadedResult> analyseOverDownloaded(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    List<AnalyseConcurrentDownloadResult> analyseConcurrentDownload(@NotNull OffsetDateTime afterTimestamp);
}
