package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PbhLicenseMapper extends BaseMapper<PbhLicense> {
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))")
    void lockKey(@Param("key") String key);

    @Insert("""
            INSERT INTO pbh_licenses (
                license_id, btn_user_id, provider, external_user_id,
                afdian_user_id, source_type, source_order_no, plan_id,
                consumed_amount, license_key, license_payload,
                start_at, expire_at
            )
            VALUES (
                #{licenseId}, #{btnUserId}, #{provider}, #{externalUserId},
                #{afdianUserId}, #{sourceType}, #{sourceOrderNo}, #{planId},
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
              AND revoked_at IS NULL
            """)
    int countDonationLicenses(@Param("userId") long userId);
}
