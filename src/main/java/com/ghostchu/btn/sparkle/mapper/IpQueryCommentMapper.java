package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IpQueryCommentMapper extends BaseMapper<IpQueryComment> {
    @Select("""
            <script>
            SELECT input.address, (
                SELECT COUNT(*) FROM ip_query_comments c
                WHERE c.deleted_at IS NULL
                  AND c.moderation_status = 'APPROVED'
                  AND c.network &gt;&gt;= input.address::inet
            ) AS count
            FROM (VALUES
              <foreach collection="addresses" item="address" separator=",">
                (#{address}::text)
              </foreach>
            ) AS input(address)
            </script>
            """)
    List<AddressCommentCount> countApprovedByAddress(@Param("addresses") List<String> addresses);

    record AddressCommentCount(String address, long count) {
    }

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

    @Select("""
            <script>
            SELECT *
            FROM ip_query_comments
            WHERE deleted_at IS NULL
              AND moderation_status = 'APPROVED'
              AND network &lt;&lt;= #{scope}::cidr
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<IpQueryComment> selectApprovedParents(@Param("scope") String scope, @Param("ids") List<Long> ids);
}
