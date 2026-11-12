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
    public Map<String, Boolean> receiveJson(
            @PathVariable String provider,
            @RequestHeader(name = "X-Sparkle-Token", required = false) String token,
            @RequestBody JsonNode payload
    ) throws BusinessException {
        memberPlatformService.ingestWebhook(
                memberPlatformService.parseProvider(provider),
                token,
                payload
        );
        return Map.of("accepted", true);
    }

    @PostMapping(
            value = "/{provider}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public Map<String, Boolean> receiveForm(
            @PathVariable String provider,
            @RequestHeader(name = "X-Sparkle-Token", required = false) String token,
            @RequestParam(name = "data", required = false) String data
    ) throws BusinessException {
        if (data == null || data.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Webhook form payload is missing data");
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(data);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Webhook form payload is not valid JSON");
        }
        memberPlatformService.ingestWebhook(
                memberPlatformService.parseProvider(provider),
                token,
                payload
        );
        return Map.of("accepted", true);
    }
}
