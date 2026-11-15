package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSuspensionService {
    private final UserMapper userMapper;
    private final UserappMapper userappMapper;

    public void setUserSuspended(User user, boolean suspended, String reason, OffsetDateTime unbanAt) throws BusinessException {
        var change = validate(suspended, reason, unbanAt);
        int updated = userMapper.update(null, Wrappers.<User>lambdaUpdate().eq(User::getId, user.getId())
                .set(User::getBannedAt, change.bannedAt())
                .set(User::getBannedReason, change.reason())
                .set(User::getUnbanAt, change.unbanAt()));
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "User not found");
        }
        user.setBannedAt(change.bannedAt()).setBannedReason(change.reason()).setUnbanAt(change.unbanAt());
        log.info("Updated user suspension: userId={}, suspended={}, unbanAt={}", user.getId(), suspended, change.unbanAt());
    }

    public void setApplicationSuspended(Userapp application, boolean suspended, String reason, OffsetDateTime unbanAt) throws BusinessException {
        updateApplication(application, suspended, reason, unbanAt, false);
    }

    public void updateApplication(Userapp application, Boolean suspended, String reason, OffsetDateTime unbanAt) throws BusinessException {
        updateApplication(application, suspended, reason, unbanAt, true);
    }

    private void updateApplication(Userapp application, Boolean suspended, String reason,
                                   OffsetDateTime unbanAt, boolean updateDetails) throws BusinessException {
        Suspension change = suspended == null ? null : validate(suspended, reason, unbanAt);
        LambdaUpdateWrapper<Userapp> update = Wrappers.<Userapp>lambdaUpdate().eq(Userapp::getId, application.getId());
        if (updateDetails) {
            update.set(Userapp::getComment, application.getComment())
                    .set(Userapp::getOwner, application.getOwner())
                    .set(Userapp::getBtnBypass, application.getBtnBypass());
        }
        if (change != null) {
            update.set(Userapp::getBannedAt, change.bannedAt())
                    .set(Userapp::getBannedReason, change.reason())
                    .set(Userapp::getUnbanAt, change.unbanAt());
        }
        if (userappMapper.update(null, update) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Application not found");
        }
        if (change != null) {
            application.setBannedAt(change.bannedAt()).setBannedReason(change.reason()).setUnbanAt(change.unbanAt());
            log.info("Updated application suspension: applicationId={}, suspended={}, unbanAt={}",
                    application.getId(), suspended, change.unbanAt());
        }
    }

    private Suspension validate(boolean suspended, String reason, OffsetDateTime unbanAt) throws BusinessException {
        if (!suspended) {
            return new Suspension(null, null, null);
        }
        String normalized = reason == null ? "" : reason.replaceAll("(?U)^\\s+|\\s+$", "");
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "A suspension reason is required");
        }
        if (normalized.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The suspension reason must not exceed 500 characters");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (unbanAt != null && !unbanAt.isAfter(now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The automatic restore time must be in the future");
        }
        return new Suspension(now, normalized, unbanAt);
    }

    private record Suspension(OffsetDateTime bannedAt, String reason, OffsetDateTime unbanAt) {
    }
}
