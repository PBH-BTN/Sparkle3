package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PrivacyLevelView;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PrivacyResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ContactEmailResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.PublicUserProfileResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.UserProfileResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.UserView;
import com.ghostchu.btn.sparkle.controller.ui.user.dto.UserRelDto;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserContactEmailService;
import com.ghostchu.btn.sparkle.service.UserPublicProfileService;
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

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/ui/user")
@RequiredArgsConstructor
public class UserApiController {
    private final IUserService userService;
    private final UserPublicProfileService publicProfileService;
    private final UserContactEmailService contactEmailService;

    @GetMapping("/profile")
    public UserProfileResponse profile(@AuthenticationPrincipal SparkleUserDetails userDetails) {
        var user = requireCurrentUser(userDetails);
        var relation = userService.getUserRelByBindUserId(user.getId());
        long scoreBytes = user.getScoreBytes() == null ? 0 : user.getScoreBytes();
        return new UserProfileResponse(
                UserView.from(user),
                relation == null ? null : new UserRelDto(relation),
                UnitConverter.autoUnit(scoreBytes),
                Long.toString(scoreBytes)
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

    @GetMapping("/privacy")
    public PrivacyResponse privacy(@AuthenticationPrincipal SparkleUserDetails userDetails) {
        var user = requireCurrentUser(userDetails);
        List<PrivacyLevelView> levels = Arrays.stream(UserPrivacyLevel.values())
                .map(PrivacyLevelView::from)
                .toList();
        return new PrivacyResponse(PrivacyLevelView.from(user.getPrivacyLevel()), levels);
    }

    @PutMapping("/privacy")
    public PrivacyResponse updatePrivacy(@RequestBody PrivacyUpdateRequest request,
                                         @AuthenticationPrincipal SparkleUserDetails userDetails) {
        if (request == null || request.level() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Privacy level is required");
        }
        var user = requireCurrentUser(userDetails);
        user.setPrivacyLevel(request.level());
        userService.updateById(user);
        userDetails.getUser().setPrivacyLevel(request.level());
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

    public record PrivacyUpdateRequest(UserPrivacyLevel level) {
    }
}
