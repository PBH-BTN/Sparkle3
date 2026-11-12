package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.MemberPlatformSyncRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MemberPlatformSyncRunMapper extends BaseMapper<MemberPlatformSyncRun> {
    @Update("""
            UPDATE member_platform_sync_runs
            SET status = 'INCOMPLETE',
                completed_at = now(),
                error_message = 'Marked incomplete after a stale running session'
            WHERE provider = #{provider}
              AND status = 'RUNNING'
              AND started_at < now() - INTERVAL '2 hours'
            """)
    int markStaleRunning(@Param("provider") String provider);
}
