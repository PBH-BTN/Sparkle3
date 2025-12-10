package com.ghostchu.btn.sparkle.controller.whatthefuck;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnnounceController {
    @GetMapping(value = {"/announce", "/tracker/announce"})
    public ResponseEntity<@NotNull String> handleAnnounce() {
        // return a bencoded string that idencate this not a tracker, with retry in never
        return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("d14:failure reason23:This is not a tracker.e15:retry in nevere");
    }
}
