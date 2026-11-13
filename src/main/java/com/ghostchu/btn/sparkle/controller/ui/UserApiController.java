package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.AvatarVisibility;
import com.ghostchu.btn.sparkle.constants.LifetimeTrafficVisibility;
import com.ghostchu.btn.sparkle.mapper.UserLifetimeTrafficMapper;
import com.ghostchu.btn.sparkle.constants.PointsVisibility;
import com.ghostchu.btn.sparkle.constants.UsernameVisibility;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PrivacySettingsView;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PrivacyResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PublicUserProfileResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.UserProfileResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.UserView;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.SendMessageRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.SendMessageResult;
import com.ghostchu.btn.sparkle.controller.ui.user.dto.UserRelDto;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserContactEmailService;
import com.ghostchu.btn.sparkle.service.UserPrivacyService;
import com.ghostchu.btn.sparkle.service.UserPublicProfileService;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.ghostchu.btn.sparkle.util.UnitConverter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ui/user")
@RequiredArgsConstructor
public class UserApiController {
    private final IUserService userService;
    private final UserLifetimeTrafficMapper lifetimeTrafficMapper;
    private final UserPublicProfileService publicProfileService;
    private final UserContactEmailService contactEmailService;
    private final UserPrivacyService privacyService;
    private final UserMessageService userMessageService;

    @GetMapping("/profile")
    public UserProfileResponse profile(@AuthenticationPrincipal SparkleUserDetails userDetails) {
        var user = requireCurrentUser(userDetails);
        var relation = userService.getUserRelByBindUserId(user.getId());
        long scoreBytes = user.getScoreBytes() == null ? 0 : user.getScoreBytes();
        return new UserProfileResponse(
                UserView.from(user, privacyService.get(user.getId())),
                relation == null ? null : new UserRelDto(relation),
                UnitConverter.autoUnit(scoreBytes),
                Long.toString(scoreBytes),
                lifetimeTrafficMapper.selectByUserId(user.getId())
        );
    }

    @GetMapping("/profile/{uid}")
    public PublicUserProfileResponse publicProfile(
            @PathVariable long uid,
            @AuthenticationPrincipal SparkleUserDetails userDetails
    ) throws com.ghostchu.btn.sparkle.exception.BusinessException {
        var currentUser = requireCurrentUser(userDetails);
        return publicProfileService.profile(currentUser.getId(), uid);
    }

    @PostMapping("/profile/{uid}/email")
    public ContactEmailResult sendContactEmail(
            @PathVariable long uid,
            @RequestBody ContactEmailRequest request,
            @AuthenticationPrincipal SparkleUserDetails userDetails,
            HttpServletRequest httpRequest
    ) throws com.ghostchu.btn.sparkle.exception.BusinessException {
        var currentUser = requireCurrentUser(userDetails);
        return contactEmailService.send(
                currentUser.getId(),
                uid,
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PostMapping("/profile/{uid}/message")
    public SendMessageResult sendMessage(
            @PathVariable long uid,
            @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal SparkleUserDetails userDetails,
            HttpServletRequest httpRequest
    ) throws com.ghostchu.btn.sparkle.exception.BusinessException {
        var currentUser = requireCurrentUser(userDetails);
        return userMessageService.sendFromUser(
                currentUser.getId(),
                uid,
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
    }

    @GetMapping("/privacy")
    public PrivacyResponse privacy(@AuthenticationPrincipal SparkleUserDetails userDetails) {
        var user = requireCurrentUser(userDetails);
        return new PrivacyResponse(
                PrivacySettingsView.from(privacyService.get(user.getId()))
        );
    }

    @PutMapping("/privacy")
    public PrivacyResponse updatePrivacy(@RequestBody PrivacyUpdateRequest request,
                                         @AuthenticationPrincipal SparkleUserDetails userDetails) {
        if (request == null
                || request.avatarVisibility() == null
                || request.usernameVisibility() == null
                || request.pointsVisibility() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "All privacy options are required"
            );
        }
        var user = requireCurrentUser(userDetails);
        privacyService.update(
                user.getId(),
                request.avatarVisibility(),
                request.usernameVisibility(),
                request.pointsVisibility(),
                request.lifetimeTrafficVisibility()
        );
        return privacy(userDetails);
    }

    private com.ghostchu.btn.sparkle.entity.User requireCurrentUser(SparkleUserDetails userDetails) {
        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var user = userService.getById(userDetails.getUserId());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    public record PrivacyUpdateRequest(
            AvatarVisibility avatarVisibility,
            UsernameVisibility usernameVisibility,
            PointsVisibility pointsVisibility,
            LifetimeTrafficVisibility lifetimeTrafficVisibility
    ) {
    }
}
