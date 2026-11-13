package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.UserCareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ui/user/career")
public class UserCareerController {
    private final UserCareerService service;

    @GetMapping("/{uid}")
    public UserCareerService.CareerView read(@PathVariable long uid,
            @AuthenticationPrincipal SparkleUserDetails principal) throws BusinessException {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return service.read(principal.getUserId(), uid);
    }
}
