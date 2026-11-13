package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.PendingLicense;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AfdianMessageLicenseMapper {
    @Select("""
            SELECT license_id::text AS license_id, external_user_id, issued_at
            FROM pbh_licenses WHERE import_match = 'UNMATCHED'
            ORDER BY issued_at, id LIMIT 100
            """)
    List<PendingLicense> pending();

    @Select("""
            SELECT * FROM pbh_licenses
            WHERE license_id = #{licenseId,typeHandler=com.ghostchu.btn.sparkle.converter.UuidTypeHandler}
              AND import_match = 'UNMATCHED' AND provider = 'AFDIAN'
            """)
    PbhLicense pendingById(UUID licenseId);

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended('sparkle:pbh-import', 0))) AS lock")
    int lock();

    @Select("SELECT * FROM pbh_licenses WHERE md5(license_key) = md5(#{key}) AND license_key = #{key} LIMIT 1")
    PbhLicense existing(String key);

    @Select("""
            SELECT * FROM member_transactions
            WHERE provider = 'AFDIAN' AND external_user_id = #{userId}
              AND external_transaction_id = #{orderId}
              AND UPPER(transaction_status) IN ('2','PAID','COMPLETED','SUCCEEDED','SUCCESS','ACTIVE')
            LIMIT 1
            """)
    MemberTransaction exact(@Param("userId") String userId, @Param("orderId") String orderId);

    @Select("""
            SELECT * FROM member_transactions
            WHERE provider = 'AFDIAN' AND external_user_id = #{userId}
              AND occurred_at BETWEEN #{createdAt}::timestamptz - INTERVAL '7 days' AND #{createdAt}
              AND UPPER(transaction_status) IN ('2','PAID','COMPLETED','SUCCEEDED','SUCCESS','ACTIVE')
            ORDER BY occurred_at DESC LIMIT 2
            """)
    List<MemberTransaction> candidates(@Param("userId") String userId,
                                        @Param("createdAt") OffsetDateTime createdAt);

    @Insert("""
            INSERT INTO pbh_licenses (license_id, provider, external_user_id, afdian_user_id,
                source_type, source_order_no, plan_id, consumed_amount, license_key, license_payload,
                issued_at, start_at, expire_at, import_fingerprint, import_message_id, import_match, imported_by)
            VALUES (#{license.licenseId,typeHandler=com.ghostchu.btn.sparkle.converter.UuidTypeHandler},
                'AFDIAN', #{license.externalUserId}, #{license.externalUserId}, #{license.sourceType},
                #{license.sourceOrderNo}, #{license.planId}, #{license.consumedAmount}, #{license.licenseKey},
                CAST(#{license.licensePayload} AS jsonb), #{license.issuedAt}, #{license.startAt}, #{license.expireAt},
                #{fingerprint}, #{messageId}, #{match}, #{operatorId})
            ON CONFLICT DO NOTHING
            """)
    int insert(@Param("license") PbhLicense license, @Param("fingerprint") String fingerprint,
               @Param("messageId") String messageId, @Param("match") String match,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE pbh_licenses SET source_type = #{type}, source_order_no = #{orderId},
                plan_id = #{sku}, import_match = #{match},
                consumed_amount = COALESCE((SELECT amount_base FROM member_transactions t
                    WHERE t.provider = pbh_licenses.provider AND t.external_user_id = pbh_licenses.external_user_id
                      AND t.external_transaction_id = #{orderId}), 0)
            WHERE id = #{id} AND import_match = 'UNMATCHED'
            """)
    int match(@Param("id") long id, @Param("type") String type, @Param("orderId") String orderId,
              @Param("sku") String sku, @Param("match") String match);

    @Update("""
            UPDATE pbh_licenses SET source_type = #{type}, source_order_no = #{orderId},
                plan_id = #{sku}, consumed_amount = #{amount}, import_match = #{match}
            WHERE id = #{id} AND import_match IN ('UNMATCHED', 'HEURISTIC')
            """)
    int matchFromMessage(@Param("id") long id, @Param("type") String type, @Param("orderId") String orderId,
                         @Param("sku") String sku, @Param("amount") java.math.BigDecimal amount,
                         @Param("match") String match);

    @Select("SELECT import_match FROM pbh_licenses WHERE id = #{id}")
    String matchStatus(long id);

}
