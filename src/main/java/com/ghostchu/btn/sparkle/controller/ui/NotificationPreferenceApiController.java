package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.NotificationPreferenceUpdate;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.NotificationPreferencesResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.NotificationPreferencesUpdate;
import com.ghostchu.btn.sparkle.controller.ui.dto.MessageModels.NotificationPreferenceView;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.NotificationPreference;
import com.ghostchu.btn.sparkle.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui/notifications")
@RequiredArgsConstructor
public class NotificationPreferenceApiController {
    private final NotificationPreferenceService preferenceService;

    @GetMapping("/preferences")
    public NotificationPreferencesResponse get(
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        return response(preferenceService.list(currentUserId(principal)));
    }

    @PutMapping("/preferences")
    public NotificationPreferencesResponse update(
            @RequestBody NotificationPreferencesUpdate request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) {
        if (request == null || request.preferences() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Notification preferences are required");
        }
        Map<NotificationCategory, Boolean[]> updates = new EnumMap<>(NotificationCategory.class);
        for (NotificationPreferenceUpdate item : request.preferences()) {
            NotificationCategory category = NotificationCategory.parse(item.category())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Unsupported notification category"
                    ));
            updates.put(category, new Boolean[]{item.inboxEnabled(), item.emailEnabled()});
        }
        return response(preferenceService.update(currentUserId(principal), updates));
    }

    private NotificationPreferencesResponse response(List<NotificationPreference> preferences) {
        return new NotificationPreferencesResponse(preferences.stream()
                .map(preference -> new NotificationPreferenceView(
                        preference.category().name(),
                        preference.inboxEnabled(),
                        preference.emailEnabled(),
                        preference.locked()
                ))
                .toList());
    }

    private long currentUserId(SparkleUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
