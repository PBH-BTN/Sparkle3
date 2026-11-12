package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.UserAccountDeletionRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserAccountDeletionRequestMapper
        extends BaseMapper<UserAccountDeletionRequest> {
    @Select("""
            SELECT *
            FROM user_account_deletion_request
            WHERE user_id = #{userId}
            FOR UPDATE
            """)
    UserAccountDeletionRequest selectForUpdate(@Param("userId") long userId);

    @Select("""
            SELECT r.bind_user_id
            FROM user_rel r
            JOIN user_account_deletion_request d ON d.user_id = r.bind_user_id
            WHERE r.platform = 'github'
              AND r.platform_user_id = #{githubUid}
              AND d.status = 'PENDING'
            LIMIT 1
            """)
    Long findPendingUserIdByGithub(@Param("githubUid") String githubUid);
}
