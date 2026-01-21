package com.ghostchu.btn.sparkle.controller.debug;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class MyIPDebugController {
    @Autowired
    protected HttpServletRequest request;


    @GetMapping("/debug/myip")
    public ResponseEntity<@NotNull String> myip() {
        return ResponseEntity.ok(request.getRemoteAddr());
    }
}

