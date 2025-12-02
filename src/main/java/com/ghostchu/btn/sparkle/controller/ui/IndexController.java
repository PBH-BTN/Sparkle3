package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页控制器
 */
@Controller
@RequiredArgsConstructor
public class IndexController extends AbstractSparkleMVC {

    @GetMapping("/")
    public String index(@AuthenticationPrincipal SparkleUserDetails userDetails, Model model) {
        return "index";
    }
}
