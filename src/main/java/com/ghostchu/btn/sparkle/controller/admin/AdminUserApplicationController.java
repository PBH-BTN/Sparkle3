package com.ghostchu.btn.sparkle.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.AdminApplicationView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.ApplicationCredentials;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/userapps")
@RequiredArgsConstructor
public class AdminUserApplicationController {
    private final IUserappService userappService;
    private final IUserService userService;

    @GetMapping
    public PageResult<AdminApplicationView> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status
    ) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        QueryWrapper<Userapp> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            wrapper.and(condition -> condition
                    .like("app_id", term)
                    .or()
                    .like("comment", term)
                    .or()
                    .like("installation_id", term));
        }
        if (ownerId != null) {
            wrapper.eq("owner", ownerId);
        }
        if ("banned".equalsIgnoreCase(status)) {
            wrapper.isNotNull("banned_at");
        } else if ("active".equalsIgnoreCase(status)) {
            wrapper.isNull("banned_at");
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        Page<Userapp> result = userappService.page(new Page<>(safePage, safeSize), wrapper);
        Map<Long, User> owners = loadOwners(result.getRecords());
        List<AdminApplicationView> records = result.getRecords().stream()
                .map(application -> toView(application, owners.get(application.getOwner())))
                .toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal(), result.getPages());
    }

    @PostMapping
    public ApplicationCredentials create(@RequestBody CreateApplicationRequest request,
                                         HttpServletRequest servletRequest) {
        User owner = requireUser(request.ownerId());
        if (request.comment() == null || request.comment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application name is required");
        }
        if (request.comment().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application name must not exceed 100 characters");
        }
        Userapp application = userappService.createUserAppForUser(
                owner.getId(),
                request.comment().trim(),
                InetAddress.ofLiteral(servletRequest.getRemoteAddr())
        );
        if (request.btnBypass() != null) {
            application.setBtnBypass(request.btnBypass());
            userappService.updateById(application);
        }
        return new ApplicationCredentials(
                toView(application, owner),
                application.getAppId(),
                application.getAppSecret()
        );
    }

    @PutMapping("/{id}")
    public AdminApplicationView update(@PathVariable long id,
                                       @RequestBody UpdateApplicationRequest request) {
        Userapp application = requireApplication(id);
        if (request.comment() == null || request.comment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application name is required");
        }
        if (request.comment().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application name must not exceed 100 characters");
        }
        User owner = requireUser(request.ownerId());
        application.setComment(request.comment().trim());
        application.setOwner(owner.getId());
        application.setBtnBypass(request.btnBypass());
        if (Boolean.TRUE.equals(request.banned())) {
            if (application.getBannedAt() == null) {
                application.setBannedAt(OffsetDateTime.now());
            }
            application.setBannedReason(
                    request.bannedReason() == null || request.bannedReason().isBlank()
                            ? "Suspended by administrator"
                            : request.bannedReason().trim()
            );
        } else {
            application.setBannedAt(null);
            application.setBannedReason(null);
        }
        userappService.update(
                Wrappers.<Userapp>lambdaUpdate()
                        .eq(Userapp::getId, id)
                        .set(Userapp::getComment, application.getComment())
                        .set(Userapp::getOwner, application.getOwner())
                        .set(Userapp::getBtnBypass, application.getBtnBypass())
                        .set(Userapp::getBannedAt, application.getBannedAt())
                        .set(Userapp::getBannedReason, application.getBannedReason())
        );
        return toView(application, owner);
    }

    @PostMapping("/{id}/reset-secret")
    public ApplicationCredentials resetSecret(@PathVariable long id) {
        Userapp current = requireApplication(id);
        Userapp application = userappService.resetUserApplicationSecret(id);
        return new ApplicationCredentials(
                toView(application, userService.getById(current.getOwner())),
                application.getAppId(),
                application.getAppSecret()
        );
    }

    @PostMapping("/{id}/ban")
    public AdminApplicationView ban(
            @PathVariable long id,
            @RequestBody BanApplicationRequest request
    ) {
        Userapp application = requireApplication(id);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A suspension reason is required");
        }
        String reason = request.reason().trim();
        if (reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The suspension reason must not exceed 500 characters");
        }
        application.setBannedAt(OffsetDateTime.now());
        application.setBannedReason(reason);
        userappService.updateById(application);
        return toView(application, userService.getById(application.getOwner()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        requireApplication(id);
        try {
            if (!userappService.deleteUserAppById(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to delete this application");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This application is still referenced by historical data",
                    exception
            );
        }
    }

    private Map<Long, User> loadOwners(List<Userapp> applications) {
        List<Long> ownerIds = applications.stream().map(Userapp::getOwner).distinct().toList();
        Map<Long, User> owners = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            userService.listByIds(ownerIds).forEach(owner -> owners.put(owner.getId(), owner));
        }
        return owners;
    }

    private Userapp requireApplication(long id) {
        Userapp application = userappService.getById(id);
        if (application == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return application;
    }

    private User requireUser(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner is required");
        }
        User user = userService.getById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner does not exist");
        }
        return user;
    }

    private AdminApplicationView toView(Userapp application, User owner) {
        return new AdminApplicationView(
                application.getId(),
                application.getAppId(),
                application.getComment(),
                application.getOwner(),
                owner == null ? null : owner.getNickname(),
                owner == null ? null : owner.getEmail(),
                application.getCreatedAt(),
                application.getLastSeenAt(),
                application.getBannedAt(),
                application.getBannedReason(),
                application.getBtnBypass(),
                application.getInstallationId(),
                application.getLastUserAgent()
        );
    }

    public record CreateApplicationRequest(
            Long ownerId,
            String comment,
            Boolean btnBypass
    ) {
    }

    public record UpdateApplicationRequest(
            Long ownerId,
            String comment,
            Boolean banned,
            String bannedReason,
            Boolean btnBypass
    ) {
    }

    public record BanApplicationRequest(
            String reason
    ) {
    }
}
