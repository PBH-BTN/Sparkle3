package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.service.MailUnsubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.ghostchu.btn.sparkle.controller.ui.dto.MailUnsubscribeModels.*;

@RestController
@RequestMapping("/api/ui/mail-unsubscribe")
@RequiredArgsConstructor
public class MailUnsubscribeController {
    private final MailUnsubscribeService service;

    @GetMapping
    public List<Option> options(@RequestHeader("X-Unsubscribe-Token") String token) {
        return service.options(token);
    }

    @PostMapping
    public void unsubscribe(@RequestHeader("X-Unsubscribe-Token") String token, @RequestBody Update request) {
        service.unsubscribe(token, request.categories());
    }

}
