package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.account.UserAccountDeletionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ui/account-deletion")
@RequiredArgsConstructor
public class AccountDeletionApiController {
    private final UserAccountDeletionService deletionService;

    @GetMapping
    public UserAccountDeletionService.AccountDeletionStatus status(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return deletionService.status(requireUserId(principal));
    }

    @PostMapping("/reauthorize")
    public ReauthenticationResponse reauthorize(
            @AuthenticationPrincipal SparkleUserDetails principal,
            HttpSession session
    ) throws BusinessException {
        var intent = deletionService.createReauthenticationIntent(requireUserId(principal));
        session.setAttribute(
                UserAccountDeletionService.REAUTH_SESSION_ATTRIBUTE,
                intent
        );
        return new ReauthenticationResponse("/oauth2/authorization/github");
    }

    private long requireUserId(SparkleUserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.getUserId();
    }

    public record ReauthenticationResponse(String redirectUrl) {
    }
}
