package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.service.impl.SwarmTrackerServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.UserappsHeartbeatServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;


@Controller
@RequestMapping("/debug/userappsHeartbeat")
public class UserappsHeartbeatDebugController {

    @Autowired
    private UserappsHeartbeatServiceImpl service;


    @GetMapping("/executeDeleteOldData")
    public ResponseEntity<@NotNull String> executeDeleteOldData() throws IOException {
        service.deleteOldData();
        return ResponseEntity.ok("OK!");
    }
}

