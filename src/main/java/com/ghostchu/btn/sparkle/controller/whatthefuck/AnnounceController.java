package com.ghostchu.btn.sparkle.controller.whatthefuck;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnounceController {
    @GetMapping(value = {"/announce", "/tracker/announce"})
    public ResponseEntity<@NotNull String> handleAnnounce() {

    }
}
