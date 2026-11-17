package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface UserAppsHeartbeatMapper extends BaseMapper<UserappsHeartbeat> {

    int upsert(UserappsHeartbeat userappsHeartbeat);

    int batchUpsert(@Param("list") List<UserappsHeartbeat> heartbeats);

    @org.apache.ibatis.annotations.Delete("""
            WITH expired AS (
                SELECT id FROM userapps_heartbeat WHERE last_seen_at <= #{cutoff}
                ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED
            )
            DELETE FROM userapps_heartbeat h USING expired e WHERE h.id = e.id
            """)
    int deleteExpiredBatch(@Param("cutoff") java.time.OffsetDateTime cutoff, @Param("limit") int limit);
}
