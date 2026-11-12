package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.MemberTransactionAuditEvent;
import org.apache.ibatis.annotations.Insert;

public interface MemberTransactionAuditEventMapper
        extends BaseMapper<MemberTransactionAuditEvent> {
    @Insert("""
            INSERT INTO member_transaction_audit_events (
                sync_run_id, provider, external_transaction_id, event_type,
                changed_fields, previous_data, current_data
            )
            VALUES (
                #{syncRunId}, #{provider}, #{externalTransactionId}, #{eventType},
                CAST(#{changedFields} AS jsonb),
                CAST(#{previousData} AS jsonb),
                CAST(#{currentData} AS jsonb)
            )
            """)
    int insertEvent(MemberTransactionAuditEvent event);
}
