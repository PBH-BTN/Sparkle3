package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.AfdianEmailBindingMapper;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfdianEmailBindingService {
    private final AfdianEmailBindingMapper mapper;
    private final UserMapper userMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean bind(String externalId, String orderId, String remark) throws BusinessException {
        if (externalId == null || !externalId.matches("[A-Za-z0-9_-]{1,128}")
                || orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,128}")) return false;
        String email = AfdianRemarkEmail.extract(remark);
        if (email == null || mapper.hasExternalBinding(externalId)) return false;
        var matches = mapper.findUsers(email);
        if (matches.size() != 1) return false;
        var user = userMapper.selectForUpdate(matches.getFirst().getId());
        if (user == null || user.getBannedAt() != null || user.getEmail() == null
                || !user.getEmail().equalsIgnoreCase(email) || mapper.hasUserBinding(user.getId())
                || mapper.hasExternalBinding(externalId)) return false;
        if (mapper.insertLegacy(user.getId(), externalId, orderId) != 1
                || mapper.insertMember(user.getId(), externalId, orderId) != 1) {
            // Roll back both writes if a manual bind or another scan won a uniqueness race.
            throw new BusinessException(HttpStatus.CONFLICT, "The membership account is already linked.");
        }
        log.info("Afdian account linked to BTN user {} using a purchase remark", user.getId());
        return true;
    }
}
