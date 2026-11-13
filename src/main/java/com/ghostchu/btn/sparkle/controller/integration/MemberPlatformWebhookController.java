package com.ghostchu.btn.sparkle.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.MemberPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives provider webhooks. Patreon sends JSON with an HMAC-MD5 signature;
 * Ko-fi sends a form field named {@code data} with a verification token inside
 * the payload. Both are acknowledged with HTTP 200 so providers stop retrying.
 */
@RestController
@RequestMapping("/api/integrations/memberships")
@RequiredArgsConstructor
public class MemberPlatformWebhookController {
    private final MemberPlatformService memberPlatformService;
    private final ObjectMapper objectMapper;

    @PostMapping(
            value = "/{provider}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> receiveJson(
            @PathVariable String provider,
            @RequestHeader(name = "X-Sparkle-Token", required = false) String token,
            @RequestHeader(name = "X-Patreon-Signature", required = false)
            String signature,
            @RequestBody String body
    ) throws BusinessException {
        JsonNode payload = parse(body);
        Map<String, String> headers = signature == null || signature.isBlank()
                ? Map.of()
                : Map.of("x-patreon-signature", signature);
        int accepted = memberPlatformService.ingestWebhook(
                memberPlatformService.parseProvider(provider),
                token,
                body,
                payload,
                headers
        );
        return Map.of("accepted", true, "records", accepted);
    }

    @PostMapping(
            value = "/{provider}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public Map<String, Object> receiveForm(
            @PathVariable String provider,
            @RequestHeader(name = "X-Sparkle-Token", required = false) String token,
            @RequestParam(name = "data", required = false) String data
    ) throws BusinessException {
        if (data == null || data.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Webhook form payload is missing data");
        }
        JsonNode payload = parse(data);
        int accepted = memberPlatformService.ingestWebhook(
                memberPlatformService.parseProvider(provider),
                token,
                data,
                payload,
                Map.of()
        );
        return Map.of("accepted", true, "records", accepted);
    }

    private JsonNode parse(String body) throws BusinessException {
        if (body == null || body.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Webhook payload is empty");
        }
        if (body.length() > 1_048_576) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Webhook payload is too large");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Webhook payload is not valid JSON");
        }
    }
}
