package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.UserContactEmailAuditView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AdminUserContactEmailService {
    private final JdbcTemplate jdbcTemplate;

    public PageResult<UserContactEmailAuditView> list(int requestedPage, int requestedSize, String keyword) {
        int page = Math.max(1, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        String term = keyword == null ? "" : keyword.trim();
        String pattern = "%" + term + "%";
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_contact_email_deliveries
                WHERE ? = ''
                   OR sender_nickname ILIKE ?
                   OR sender_email ILIKE ?
                   OR recipient_nickname ILIKE ?
                   OR recipient_email ILIKE ?
                   OR subject ILIKE ?
                   OR sender_ip ILIKE ?
                   OR sender_user_agent ILIKE ?
                """, Long.class,
                term, pattern, pattern, pattern, pattern, pattern, pattern, pattern
        );
        long safeTotal = total == null ? 0 : total;
        var records = jdbcTemplate.query("""
                SELECT id, sender_user_id, sender_nickname, sender_email,
                       recipient_user_id, recipient_nickname, recipient_email,
                       subject, sender_ip, sender_user_agent, sent_at
                FROM user_contact_email_deliveries
                WHERE ? = ''
                   OR sender_nickname ILIKE ?
                   OR sender_email ILIKE ?
                   OR recipient_nickname ILIKE ?
                   OR recipient_email ILIKE ?
                   OR subject ILIKE ?
                   OR sender_ip ILIKE ?
                   OR sender_user_agent ILIKE ?
                ORDER BY sent_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (resultSet, rowNum) -> new UserContactEmailAuditView(
                resultSet.getLong("id"),
                String.valueOf(resultSet.getLong("sender_user_id")),
                resultSet.getString("sender_nickname"),
                resultSet.getString("sender_email"),
                String.valueOf(resultSet.getLong("recipient_user_id")),
                resultSet.getString("recipient_nickname"),
                resultSet.getString("recipient_email"),
                resultSet.getString("subject"),
                resultSet.getString("sender_ip"),
                resultSet.getString("sender_user_agent"),
                resultSet.getObject("sent_at", OffsetDateTime.class)
        ), term, pattern, pattern, pattern, pattern, pattern, pattern, pattern,
                size, (long) (page - 1) * size);
        long pages = safeTotal == 0 ? 0 : (safeTotal + size - 1) / size;
        return new PageResult<>(records, page, size, safeTotal, pages);
    }
}
