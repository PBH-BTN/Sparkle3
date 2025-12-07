package com.ghostchu.btn.sparkle.controller.ui.tracker;

import com.ghostchu.btn.sparkle.controller.ui.AbstractSparkleMVC;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Controller
public class TrackerController extends AbstractSparkleMVC {

    @Value("${sparkle.root-url}")
    private String rootUrl;

    @Value("${sparkle.tracker.endpoint}")
    private String announceEndpoint;

    @GetMapping("/tracker/dashboard")
    public ResponseEntity<byte @NotNull []> trackerDashboard() throws IOException {
        File file = new File("data/sparkle_tracker_dashboard.png");
        if (!file.exists()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(Files.readAllBytes(file.toPath()));
    }

    @GetMapping("/tracker")
    public String trackerIndex(Model model) {
        model.addAttribute("trackerDashboardUrl", rootUrl + "/tracker/dashboard");
        model.addAttribute("announceUrl", announceEndpoint);
        return "tracker/index";
    }
}
