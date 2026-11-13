package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IpQueryCommentMapper extends BaseMapper<IpQueryComment> {
    @Select("""
            SELECT *
            FROM ip_query_comments
            WHERE deleted_at IS NULL
              AND network <<= #{scope}::cidr
              AND (
                    moderation_status = 'APPROVED'
                    OR author_user_id = #{viewerUserId}
                  )
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<IpQueryComment> selectVisiblePage(
            @Param("scope") String scope,
            @Param("viewerUserId") long viewerUserId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Select("""
            SELECT COUNT(*)
            FROM ip_query_comments
            WHERE deleted_at IS NULL
              AND network <<= #{scope}::cidr
              AND (
                    moderation_status = 'APPROVED'
                    OR author_user_id = #{viewerUserId}
                  )
            """)
    long countVisible(
            @Param("scope") String scope,
            @Param("viewerUserId") long viewerUserId
    );
}
