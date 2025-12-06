package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;

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
}
