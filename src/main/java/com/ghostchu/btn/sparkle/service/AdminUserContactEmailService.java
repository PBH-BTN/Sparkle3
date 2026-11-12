package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.UserContactEmailAuditView;
import com.ghostchu.btn.sparkle.entity.UserContactEmailDelivery;
import com.ghostchu.btn.sparkle.mapper.UserContactEmailDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AdminUserContactEmailService {
    private final UserContactEmailDeliveryMapper deliveryMapper;

    public PageResult<UserContactEmailAuditView> list(int requestedPage, int requestedSize, String keyword) {
        int page = Math.max(1, requestedPage);
        int size = Math.min(100, Math.max(1, requestedSize));
        String term = keyword == null ? "" : keyword.trim();
        QueryWrapper<UserContactEmailDelivery> wrapper = new QueryWrapper<>();
        if (!term.isBlank()) {
            wrapper.and(query -> query
                    .like("sender_nickname", term).or().like("sender_email", term)
                    .or().like("recipient_nickname", term).or().like("recipient_email", term)
                    .or().like("subject", term).or().like("sender_ip", term)
                    .or().like("sender_user_agent", term));
        }
        wrapper.orderByDesc("sent_at").orderByDesc("id");
        Page<UserContactEmailDelivery> result =
                deliveryMapper.selectPage(new Page<>(page, size), wrapper);
        long safeTotal = result.getTotal();
        var records = result.getRecords().stream()
                .map(entity -> new UserContactEmailAuditView(
                        entity.getId(),
                        String.valueOf(entity.getSenderUserId()),
                        entity.getSenderNickname(),
                        entity.getSenderEmail(),
                        String.valueOf(entity.getRecipientUserId()),
                        entity.getRecipientNickname(),
                        entity.getRecipientEmail(),
                        entity.getSubject(),
                        entity.getSenderIp(),
                        entity.getSenderUserAgent(),
                        entity.getSentAt()
                )).toList();
        long pages = safeTotal == 0 ? 0 : (safeTotal + size - 1) / size;
        return new PageResult<>(records, page, size, safeTotal, pages);
    }
}
