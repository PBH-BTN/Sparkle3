package com.ghostchu.btn.sparkle.controller.integration;

import com.ghostchu.btn.sparkle.controller.integration.dto.FlarumIntegrationModels.UserDataResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.FlarumIntegrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/integrations/flarum")
public class FlarumIntegrationController {
    private final FlarumIntegrationService integrationService;
    private final String expectedToken;

    public FlarumIntegrationController(
            FlarumIntegrationService integrationService,
            @Value("${sparkle.flarum-integration.token:}") String expectedToken
    ) {
        this.integrationService = integrationService;
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    @GetMapping("/user-by-github")
    public UserDataResponse userByGithub(
            @RequestParam("githubUid") String githubUid,
            @RequestHeader(name = "X-Sparkle-Token", required = false) String providedToken
    ) throws BusinessException {
        verifyToken(providedToken);
        return integrationService.findByGithubUid(githubUid);
    }

    private void verifyToken(String providedToken) throws BusinessException {
        if (expectedToken.isBlank()) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Flarum integration token is not configured"
            );
        }
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken == null
                ? new byte[0]
                : providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid integration token");
        }
    }
}
