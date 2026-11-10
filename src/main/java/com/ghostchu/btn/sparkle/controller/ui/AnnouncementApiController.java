package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.PublicAnnouncementView;
import com.ghostchu.btn.sparkle.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ui/announcements")
@RequiredArgsConstructor
public class AnnouncementApiController {
    private final AnnouncementService announcementService;

    @GetMapping
    public List<PublicAnnouncementView> list() {
        return announcementService.listActive();
    }
}
