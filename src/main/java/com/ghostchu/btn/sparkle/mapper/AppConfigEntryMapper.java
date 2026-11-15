package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.AppConfigEntry;
import org.apache.ibatis.annotations.Insert;

public interface AppConfigEntryMapper extends BaseMapper<AppConfigEntry> {
    @Insert("""
            INSERT INTO app_config_entries
                (config_key, config_value, value_type, config_group, source, sensitive, hot_reload, updated_at)
            VALUES
                (#{configKey}, #{configValue}, #{valueType}, #{configGroup}, #{source}, #{sensitive}, #{hotReload}, #{updatedAt})
            ON CONFLICT (config_key) DO NOTHING
            """)
    int insertIfAbsent(AppConfigEntry entry);
}
