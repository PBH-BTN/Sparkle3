package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.AnalyseRule;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseByModuleResult;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseConcurrentDownloadResult;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseOverDownloadedResult;
import com.ghostchu.btn.sparkle.mapper.customresult.AnalyseIPAndIdentityResult;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.ResultHandler;
import org.jetbrains.annotations.NotNull;

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
    Cursor<AnalyseByModuleResult> analyseByModule(@NotNull OffsetDateTime afterTimestamp, @NotNull List<String> moduleName);

    @NotNull
    Cursor<AnalyseOverDownloadedResult> analyseOverDownloaded(@NotNull OffsetDateTime afterTimestamp);

    void analyseOverDownloadedWithHandler(@NotNull OffsetDateTime afterTimestamp, ResultHandler<AnalyseOverDownloadedResult> handler);

    void analyseOverDownloadedFromMaterializedViewWithHandler(@NotNull OffsetDateTime afterTimestamp, ResultHandler<AnalyseOverDownloadedResult> handler);

    void refreshOverDownloadMaterializedView();

    @NotNull
    Cursor<AnalyseConcurrentDownloadResult> analyseConcurrentDownload(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<AnalyseIPAndIdentityResult> analyseRandomIdentityBanHistory(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<AnalyseIPAndIdentityResult> analyseRandomIdentitySwarmTracker(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<AnalyseIPAndIdentityResult> analyseRain000IdentityBanHistory(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<AnalyseIPAndIdentityResult> analyseRain000IdentitySwarmTracker(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<AnalyseIPAndIdentityResult> analyseGopeeddevIdentityBanHistory(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<AnalyseIPAndIdentityResult> analyseGopeeddevIdentitySwarmTracker(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<String> analyseDatacenterHighRiskBanHistory(@NotNull OffsetDateTime afterTimestamp);

    @NotNull
    Cursor<String> analyseDatacenterHighRiskSwarmTracker(@NotNull OffsetDateTime afterTimestamp);
}
