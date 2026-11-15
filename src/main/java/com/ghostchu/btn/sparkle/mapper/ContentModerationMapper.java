package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ContentModerationMapper {
    @Select("SELECT version, enabled, words FROM content_moderation_dictionary WHERE id = 1")
    Dictionary read();

    @Select("SELECT version FROM content_moderation_dictionary WHERE id = 1")
    long version();

    @Update("""
            UPDATE content_moderation_dictionary
            SET version = version + 1, enabled = #{enabled}, words = #{words}
            WHERE id = 1 AND version = #{version}
            """)
    int save(@Param("version") long version, @Param("enabled") boolean enabled, @Param("words") String words);

    record Dictionary(long version, boolean enabled, String words) {}
}
