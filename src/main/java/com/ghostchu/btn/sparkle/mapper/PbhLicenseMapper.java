package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PbhLicenseMapper extends BaseMapper<PbhLicense> {
    @Select("""
            SELECT COUNT(*) FROM pbh_licenses l JOIN member_platform_bindings b
              ON b.provider = l.provider AND b.external_user_id = l.userid
            WHERE b.btn_user_id = #{userId} AND l.import_match = 'UNMATCHED'
            """)
    int countUnmatchedImports(@Param("userId") long userId);

    @Select("""
            SELECT 1
            FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))) AS lock
            """)
    int lockKey(@Param("key") String key);

    @Insert("""
            INSERT INTO pbh_licenses (
                license_id, btn_user_id, provider, userid,
                source_type, source_order_no, plan_id,
                consumed_amount, license_key, license_payload,
                start_at, expire_at
            )
            VALUES (
                #{licenseId,typeHandler=com.ghostchu.btn.sparkle.converter.UuidTypeHandler},
                #{btnUserId}, #{provider}, #{externalUserId},
                #{sourceType}, #{sourceOrderNo}, #{planId},
                #{consumedAmount}, #{licenseKey}, CAST(#{licensePayload} AS jsonb),
                #{startAt}, #{expireAt}
            )
            """)
    int insertWithPayload(PbhLicense license);

    @Select("""
            SELECT COUNT(*)
            FROM pbh_licenses
            WHERE btn_user_id = #{userId}
              AND source_type = 'DONATION'
              AND import_fingerprint IS NULL
              AND revoked_at IS NULL
            """)
    int countDonationLicenses(@Param("userId") long userId);
}
