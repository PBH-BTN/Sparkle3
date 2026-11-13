package com.ghostchu.btn.sparkle.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.AdminUserView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.mapper.UserRelMapper;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.UserPrivacySettings;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private static final OffsetDateTime NEVER = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final IUserService userService;
    private final IUserappService userappService;
    private final UserRelMapper userRelMapper;
    private final PointsService pointsService;
    private final UserPrivacyService userPrivacyService;

    @GetMapping
    public PageResult<AdminUserView> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status
    ) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String search = keyword.trim();
            Long id = parseLong(search);
            wrapper.and(condition -> {
                condition.apply("LOWER(email) LIKE LOWER({0})", "%" + search + "%")
                        .or()
                        .apply("LOWER(nickname) LIKE LOWER({0})", "%" + search + "%");
                if (id != null) {
                    condition.or().eq("id", id);
                }
            });
        }
        if (role != null && !role.isBlank()) {
            wrapper.eq("role", role.trim());
        }
        if ("banned".equalsIgnoreCase(status)) {
            wrapper.isNotNull("banned_at");
        } else if ("active".equalsIgnoreCase(status)) {
            wrapper.isNull("banned_at");
        }
        wrapper.orderByDesc("register_at").orderByDesc("id");

        Page<User> result = userService.page(new Page<>(safePage, safeSize), wrapper);
        Map<Long, String> relations = loadGitHubRelations(result.getRecords());
        Map<Long, UserPrivacySettings> privacy = userPrivacyService.getAll(
                result.getRecords().stream().map(User::getId).toList()
        );
        List<AdminUserView> records = result.getRecords().stream()
                .map(user -> toView(
                        user,
                        relations.get(user.getId()),
                        privacy.getOrDefault(
                                user.getId(),
                                UserPrivacySettings.defaults(user.getId())
                        )
                ))
                .toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal(), result.getPages());
    }

    @PostMapping
    @Transactional
    public AdminUserView create(@RequestBody CreateUserRequest request) {
        validateUserValues(request.email(), request.nickname());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userService.count(new QueryWrapper<User>().eq("email", email)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        User user = new User()
                .setEmail(email)
                .setNickname(request.nickname().trim())
                .setAvatar(blankToDefault(request.avatarUrl(), "/img/anonymous-user.png"))
                .setNicknameModerationStatus("APPROVED")
                .setAvatarModerationStatus("APPROVED")
                .setRole(normalizeRole(request.role()))
                .setRegisterAt(OffsetDateTime.now())
                .setLastLoginAt(NEVER);
        userService.save(user);
        userPrivacyService.ensure(user.getId());
        UserPrivacySettings privacy = userPrivacyService.update(
                user.getId(),
                request.avatarVisibility() == null
                        ? AvatarVisibility.BLURRED : request.avatarVisibility(),
                request.usernameVisibility() == null
                        ? UsernameVisibility.BLURRED : request.usernameVisibility(),
                request.pointsVisibility() == null
                        ? PointsVisibility.SHOW : request.pointsVisibility()
        );
        return toView(user, null, privacy);
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminUserView update(@PathVariable long id,
                                @RequestBody UpdateUserRequest request,
                                @AuthenticationPrincipal SparkleUserDetails principal) {
        User user = requireUser(id);
        validateUserValues(request.email(), request.nickname());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userService.count(new QueryWrapper<User>()
                .eq("email", email)
                .ne("id", id)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        String role = normalizeRole(request.role());
        if (principal != null && principal.getUserId() == id && !"admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own admin role");
        }
        user.setEmail(email)
                .setNickname(request.nickname().trim())
                .setAvatar(blankToDefault(request.avatarUrl(), "/img/anonymous-user.png"))
                .setNicknameModerationStatus("APPROVED")
                .setAvatarModerationStatus("APPROVED")
                .setNicknameModerationReason(null)
                .setAvatarModerationReason(null)
                .setRole(role);
        userService.updateById(user);
        UserPrivacySettings privacy = userPrivacyService.update(
                id,
                request.avatarVisibility() == null
                        ? AvatarVisibility.BLURRED : request.avatarVisibility(),
                request.usernameVisibility() == null
                        ? UsernameVisibility.BLURRED : request.usernameVisibility(),
                request.pointsVisibility() == null
                        ? PointsVisibility.SHOW : request.pointsVisibility()
        );
        UserRel relation = userService.getUserRelByBindUserId(id);
        return toView(
                user,
                relation == null ? null : relation.getPlatformUserLogin(),
                privacy
        );
    }

    @PostMapping("/{id}/ban")
    public AdminUserView setBanned(@PathVariable long id,
                                   @RequestBody BanUserRequest request,
                                   @AuthenticationPrincipal SparkleUserDetails principal) {
        User user = requireUser(id);
        if (request.banned() && principal != null && principal.getUserId() == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot suspend your own account");
        }
        if (request.banned()) {
            user.setBannedAt(OffsetDateTime.now());
            user.setBannedReason(blankToDefault(request.reason(), "Suspended by administrator"));
            userService.updateById(user);
        } else {
            user.setBannedAt(null);
            user.setBannedReason(null);
            userService.update(
                    Wrappers.<User>lambdaUpdate()
                            .eq(User::getId, id)
                            .set(User::getBannedAt, null)
                            .set(User::getBannedReason, null)
            );
        }
        UserRel relation = userService.getUserRelByBindUserId(id);
        return toView(
                user,
                relation == null ? null : relation.getPlatformUserLogin(),
                userPrivacyService.get(id)
        );
    }

    @PostMapping("/{id}/points")
    public AdminUserView adjustPoints(
            @PathVariable long id,
            @RequestBody AdjustPointsRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        requireUser(id);
        long delta;
        try {
            delta = Long.parseLong(request.amount() == null ? "" : request.amount().trim());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid signed point amount is required"
            );
        }
        pointsService.adjustByAdmin(
                id,
                principal.getUserId(),
                delta,
                request.reason()
        );
        User updated = requireUser(id);
        UserRel relation = userService.getUserRelByBindUserId(id);
        return toView(
                updated,
                relation == null ? null : relation.getPlatformUserLogin(),
                userPrivacyService.get(id)
        );
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable long id, @AuthenticationPrincipal SparkleUserDetails principal) {
        requireUser(id);
        if (principal != null && principal.getUserId() == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
        }
        if (userService.getSystemUids().contains(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "System users cannot be deleted");
        }
        long appCount = userappService.count(new QueryWrapper<Userapp>().eq("owner", id));
        if (appCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Delete this user's applications before deleting the account"
            );
        }
        userRelMapper.delete(new QueryWrapper<UserRel>().eq("bind_user_id", id));
        if (!userService.removeById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to delete this user");
        }
    }

    private Map<Long, String> loadGitHubRelations(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, String> relations = new HashMap<>();
        userRelMapper.selectList(new QueryWrapper<UserRel>().in("bind_user_id", ids))
                .forEach(relation -> relations.put(relation.getBindUserId(), relation.getPlatformUserLogin()));
        return relations;
    }

    private User requireUser(long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private AdminUserView toView(
            User user,
            String githubLogin,
            UserPrivacySettings privacy
    ) {
        return new AdminUserView(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                privacy.avatarVisibility().name(),
                privacy.usernameVisibility().name(),
                privacy.pointsVisibility().name(),
                user.getRegisterAt(),
                user.getLastLoginAt(),
                user.getBannedAt(),
                user.getBannedReason(),
                githubLogin,
                Long.toString(user.getScoreBytes() == null ? 0 : user.getScoreBytes())
        );
    }

    private void validateUserValues(String email, String nickname) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required");
        }
        if (email.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not exceed 255 characters");
        }
        if (nickname == null || nickname.isBlank() || nickname.trim().length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nickname is required and must not exceed 255 characters"
            );
        }
    }

    private String normalizeRole(String role) {
        if ("admin".equalsIgnoreCase(role)) {
            return "admin";
        }
        if ("moderator".equalsIgnoreCase(role)) {
            return "moderator";
        }
        return "user";
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record CreateUserRequest(
            String email,
            String nickname,
            String avatarUrl,
            String role,
            AvatarVisibility avatarVisibility,
            UsernameVisibility usernameVisibility,
            PointsVisibility pointsVisibility
    ) {
    }

    public record UpdateUserRequest(
            String email,
            String nickname,
            String avatarUrl,
            String role,
            AvatarVisibility avatarVisibility,
            UsernameVisibility usernameVisibility,
            PointsVisibility pointsVisibility
    ) {
    }

    public record BanUserRequest(boolean banned, String reason) {
    }

    public record AdjustPointsRequest(String amount, String reason) {
    }
}
