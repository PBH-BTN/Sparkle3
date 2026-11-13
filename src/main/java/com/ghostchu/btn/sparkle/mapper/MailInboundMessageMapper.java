package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.MailInboundMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

public interface MailInboundMessageMapper extends BaseMapper<MailInboundMessage> {
    @Select("""
            SELECT uid
            FROM mail_inbound_messages
            WHERE mailbox = #{mailbox}
            """)
    Set<String> selectUids(@Param("mailbox") String mailbox);

    @Select("""
            SELECT COUNT(*)
            FROM mail_inbound_messages
            WHERE status = 'IMPORTED'
            """)
    long countImported();
}
