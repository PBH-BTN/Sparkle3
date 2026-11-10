package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.UiModels.TrackerResponse;
import com.ghostchu.btn.sparkle.service.AdminConfigService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/ui/tracker")
@RequiredArgsConstructor
public class TrackerApiController {
    @Value("${sparkle.root-url}")
    private String rootUrl;

    private final AdminConfigService adminConfigService;

    @GetMapping
    public TrackerResponse tracker() {
        return new TrackerResponse(
                adminConfigService.get().trackerEndpoint(),
                rootUrl + "/api/ui/tracker/dashboard"
        );
    }

    @GetMapping("/dashboard")
    public ResponseEntity<byte @NotNull []> dashboard() throws IOException {
        File file = new File("data/sparkle_tracker_dashboard.png");
        if (!file.exists()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(Files.readAllBytes(file.toPath()));
    }
}
