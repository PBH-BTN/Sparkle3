package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.UserContactEmailDelivery;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserContactEmailDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserContactEmailReservationService {
    private final UserContactEmailDeliveryMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public int reserve(UserContactEmailDelivery delivery, int limit) throws BusinessException {
        mapper.lockKey("sparkle:user-contact-email:" + delivery.getSenderUserId());
        int used = mapper.countToday(delivery.getSenderUserId());
        if (used >= limit) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Daily contact email limit reached");
        }
        mapper.insert(delivery);
        return limit - used - 1;
    }
}
