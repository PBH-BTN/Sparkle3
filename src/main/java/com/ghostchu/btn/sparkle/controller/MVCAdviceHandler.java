package com.ghostchu.btn.sparkle.controller;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class MVCAdviceHandler {
    @ModelAttribute("sessionUser")
    public User addUserToModel(HttpServletRequest request, @AuthenticationPrincipal SparkleUserDetails userDetails) {
        if(userDetails != null) {
            return userDetails.getUser();
        }
        return null;
    }
}
