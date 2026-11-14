package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface PbhLicenseRevocationMapper {
    @Insert("""
            INSERT INTO pbh_license_revocations (fingerprint, field_hashes, reason, revoked_by)
            VALUES (#{fingerprint}, CAST(#{hashes} AS jsonb), #{reason}, #{actor})
            ON CONFLICT (fingerprint) DO NOTHING
            """)
    int revoke(@Param("fingerprint") String fingerprint, @Param("hashes") String hashes,
               @Param("reason") String reason, @Param("actor") long actor);

    @Select("SELECT EXISTS(SELECT 1 FROM pbh_license_revocations WHERE fingerprint = #{fingerprint})")
    boolean isRevoked(String fingerprint);

    @Select("""
            SELECT fingerprint, reason, revoked_at AS "revokedAt", revoked_by::text AS "revokedBy"
            FROM pbh_license_revocations ORDER BY revoked_at DESC, fingerprint LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> list(@Param("size") int size, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM pbh_license_revocations")
    long count();

    @Update("""
            UPDATE pbh_licenses SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = #{reason}
            WHERE license_key = #{key} AND revoked_at IS NULL
            """)
    int markLicense(@Param("key") String key, @Param("reason") String reason);
}
