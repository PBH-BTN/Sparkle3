package com.ghostchu.btn.sparkle.controller.ui.user;

import com.ghostchu.btn.sparkle.controller.ui.user.dto.UserDto;
import com.ghostchu.btn.sparkle.controller.ui.user.dto.UserRelDto;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserViewController {
    @Autowired
    private IUserService userService;

    @GetMapping({"/user", "/user/profile"})
    public String profile(Model model, @AuthenticationPrincipal SparkleUserDetails userDetails) {
        User user = userService.getById(userDetails.getUserId());
        UserRel userRel = userService.getUserRelByBindUserId(user.getId());
        UserRelDto userRelDto = null;
        if(userRel != null) {
            userRelDto = new UserRelDto(userRel);
        }
        model.addAttribute("user", user);
        model.addAttribute("userRel", userRelDto);
        model.addAttribute("userScoreBytesDisplay", "N/A");
        model.addAttribute("userScoreBytesRaw", -1);
        return "user/profile";
    }

    @GetMapping("/user/privacy-mode")
    public String privacyMode(Model model, @AuthenticationPrincipal SparkleUserDetails userDetails) {
        User user = userService.getById(userDetails.getUserId());
        model.addAttribute("user", user);
        model.addAttribute("privacyLevels", UserPrivacyLevel.values());
        return "user/privacy-mode";
    }

    @PostMapping("/user/privacy-mode")
    public String savePrivacyMode(@RequestParam("level") UserPrivacyLevel level,
                                  @AuthenticationPrincipal SparkleUserDetails userDetails) {
        User user = userService.getById(userDetails.getUserId());
        user.setPrivacyLevel(level);
        userService.updateById(user);
        return "redirect:/user/privacy-mode?updated=true";
    }
}
