package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.ApplicationCredentials;
import com.ghostchu.btn.sparkle.controller.ui.userapp.dto.UserApplicationDto;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/ui/userapps")
@RequiredArgsConstructor
public class UserApplicationApiController {
    private final IUserappService userappService;
    private final IUserappsHeartbeatService heartbeatService;

    @GetMapping
    public List<UserApplicationDto> list(@AuthenticationPrincipal SparkleUserDetails userDetails) {
        requirePrincipal(userDetails);
        var applications = userappService.getUserAppsByUserId(userDetails.getUserId())
                .stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();
        var exemptAppIds = applications.stream()
                .filter(application -> Boolean.TRUE.equals(application.getBtnBypass()))
                .filter(application -> application.getBannedAt() == null)
                .map(application -> application.getId())
                .toList();
        Map<Long, LinkedHashSet<String>> ipsByApp = new HashMap<>();
        if (!exemptAppIds.isEmpty()) {
            OffsetDateTime endAt = OffsetDateTime.now();
            var heartbeats = heartbeatService.fetchHeartBeatsByUserAppIdsInTimeRange(
                    exemptAppIds,
                    endAt.minusHours(1),
                    endAt
            );
            for (var heartbeat : heartbeats) {
                ipsByApp.computeIfAbsent(heartbeat.getUserappId(), ignored -> new LinkedHashSet<>())
                        .add(heartbeat.getIp().getHostAddress());
            }
        }
        return applications.stream()
                .map(application -> new UserApplicationDto(
                        application,
                        new ArrayList<>(ipsByApp.getOrDefault(
                                application.getId(),
                                new LinkedHashSet<>()
                        ))
                ))
                .toList();
    }

    @PostMapping
    public ApplicationCredentials create(@RequestBody CreateUserApplicationRequest request,
                                         @AuthenticationPrincipal SparkleUserDetails userDetails,
                                         HttpServletRequest servletRequest) {
        requirePrincipal(userDetails);
        String comment = request == null ? null : request.comment();
        if (comment == null || comment.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application name is required");
        }
        comment = comment.trim();
        if (comment.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application name must not exceed 100 characters");
        }
        var application = userappService.createUserAppForUser(
                userDetails.getUserId(),
                comment,
                InetAddress.ofLiteral(servletRequest.getRemoteAddr())
        );
        return credentials(application);
    }

    @PostMapping({"/{appId}/reset-secret", "/{appId}/resetAppSecret"})
    public ApplicationCredentials resetSecret(@PathVariable String appId,
                                              @AuthenticationPrincipal SparkleUserDetails userDetails)
            throws UserApplicationNotFoundException, AccessDeniedException {
        var application = requireOwnedApplication(appId, userDetails);
        return credentials(userappService.resetUserApplicationSecret(application.getId()));
    }

    @DeleteMapping("/{appId}")
    public void delete(@PathVariable String appId,
                       @AuthenticationPrincipal SparkleUserDetails userDetails)
            throws UserApplicationNotFoundException, AccessDeniedException {
        var application = requireOwnedApplication(appId, userDetails);
        userappService.deleteUserAppById(application.getId());
    }

    private com.ghostchu.btn.sparkle.entity.Userapp requireOwnedApplication(
            String appId,
            SparkleUserDetails userDetails
    ) throws UserApplicationNotFoundException, AccessDeniedException {
        requirePrincipal(userDetails);
        var application = userappService.getUserAppByAppId(appId);
        if (application == null) {
            throw new UserApplicationNotFoundException();
        }
        if (!Objects.equals(application.getOwner(), userDetails.getUserId())) {
            throw new AccessDeniedException("Permission denied");
        }
        return application;
    }

    private void requirePrincipal(SparkleUserDetails userDetails) {
        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private ApplicationCredentials credentials(com.ghostchu.btn.sparkle.entity.Userapp application) {
        return new ApplicationCredentials(
                new UserApplicationDto(application, List.of()),
                application.getAppId(),
                application.getAppSecret()
        );
    }

    public record CreateUserApplicationRequest(String comment) {
    }
}
