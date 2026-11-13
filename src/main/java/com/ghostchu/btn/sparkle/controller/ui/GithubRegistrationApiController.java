package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.PendingGithubRegistration;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.BlurredAvatarDiskCache;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.points.PointsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;

@RestController
@RequestMapping("/api/ui/registration")
@RequiredArgsConstructor
@Slf4j
public class GithubRegistrationApiController {
    private final IUserService userService;
    private final BlurredAvatarDiskCache blurredAvatarDiskCache;
    private final PointsService pointsService;

    @GetMapping
    public PendingRegistrationView pending(
            Authentication authentication,
            HttpSession session
    ) {
        PendingGithubRegistration registration = requirePending(
                authentication,
                session
        );
        return new PendingRegistrationView(
                registration.githubLogin(),
                registration.nickname(),
                registration.avatarUrl(),
                registration.email()
        );
    }

    @PostMapping
    public RegistrationResult register(
            Authentication authentication,
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession();
        PendingGithubRegistration registration = requirePending(
                authentication,
                session
        );
        User user = userService.userGithubOAuthLogin(
                authentication,
                InetAddress.ofLiteral(request.getRemoteAddr()),
                registration.githubUid(),
                registration.githubLogin(),
                registration.avatarUrl(),
                registration.nickname(),
                registration.email()
        );

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        SparkleUserDetails userDetails = new SparkleUserDetails(user, oauth2User);
        UsernamePasswordAuthenticationToken registeredAuthentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        authentication.getCredentials(),
                        userDetails.getAuthorities()
                );
        registeredAuthentication.setDetails(authentication.getDetails());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(registeredAuthentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        session.removeAttribute(PendingGithubRegistration.SESSION_ATTRIBUTE);

        blurredAvatarDiskCache.refreshOnLogin(user.getId());
        try {
            pointsService.awardDailyLogin(user.getId());
        } catch (Exception exception) {
            log.warn("Unable to award registration login points to user {}: {}",
                    user.getId(), exception.getMessage());
        }
        log.info("Completed GitHub registration for new user {}", user.getId());
        return new RegistrationResult(true, String.valueOf(user.getId()));
    }

    private PendingGithubRegistration requirePending(
            Authentication authentication,
            HttpSession session
    ) {
        Object value = session.getAttribute(PendingGithubRegistration.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingGithubRegistration registration)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "GitHub registration is not pending"
            );
        }
        if (authentication == null
                || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Object githubId = oauth2User.getAttribute("id");
        if (!(githubId instanceof Number number)
                || number.longValue() != registration.githubUid()) {
            session.removeAttribute(PendingGithubRegistration.SESSION_ATTRIBUTE);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The authorized GitHub account changed"
            );
        }
        return registration;
    }

    public record PendingRegistrationView(
            String githubLogin,
            String nickname,
            String avatarUrl,
            String email
    ) {
    }

    public record RegistrationResult(boolean registered, String userId) {
    }
}
