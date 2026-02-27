package com.ghostchu.btn.sparkle.controller.ui.userapp;

import com.ghostchu.btn.sparkle.controller.ui.userapp.dto.UserApplicationDto;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.TooManyUserApplicationException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.StringJoiner;

@Controller
public class UserApplicationViewController {

    @Autowired
    private IUserService userService;
    @Autowired
    private IUserappService userappService;
    @Autowired
    private IUserappsHeartbeatService heartbeatService;

    @GetMapping("/userapp")
    public String userApplicationIndex(Model model, @AuthenticationPrincipal SparkleUserDetails userDetails) {
        var list = userappService.getUserAppsByUserId(userDetails.getUserId())
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(entity->{
                   var heartBeats = heartbeatService.fetchHeartBeatsByUserAppIdInTimeRange(entity.getId(), OffsetDateTime.now().minusHours(1), OffsetDateTime.now());
                    StringJoiner joiner = new StringJoiner("\n");
                    joiner.add("此用户应用程序正在关联以下 IP 地址:");
                    heartBeats.forEach(h->joiner.add(h.getIp().getHostAddress()));
                    return new UserApplicationDto(entity, joiner.toString());
                }).toList();
        model.addAttribute("userapps", list);
        return "userapp/index";
    }

    @PostMapping("/userapp/{appId}/resetAppSecret")
    public String resetUserApplicationAppSecret(Model model, @PathVariable("appId") String appId, @AuthenticationPrincipal SparkleUserDetails userDetails) throws UserApplicationNotFoundException, AccessDeniedException {
        var userApp = userappService.getUserAppByAppId(appId);
        if (userApp == null) {
            throw new UserApplicationNotFoundException();
        }
        if (!Objects.equals(userApp.getOwner(), userDetails.getUserId()))
            throw new AccessDeniedException("Permission denied");
        var resetUsrApp = userappService.resetUserApplicationSecret(userApp.getId());
        model.addAttribute("userapp", resetUsrApp);
        return "userapp/created";
    }


    @PostMapping("/userapp/{appId}/delete")
    public String deleteUserApplication(@PathVariable("appId") String appId, @AuthenticationPrincipal SparkleUserDetails userDetails) throws UserApplicationNotFoundException, AccessDeniedException {
        var userApp = userappService.getUserAppByAppId(appId);
        if (userApp == null) {
            throw new UserApplicationNotFoundException();
        }
        if (!Objects.equals(userApp.getOwner(), userDetails.getUserId()))
            throw new AccessDeniedException("Permission denied");
        userappService.deleteUserAppById(userApp.getId());
        return "redirect:/userapp?deleteSuccess=true";
    }

    @GetMapping("/userapp/create")
    public String createUserApplication() {
        return "userapp/create";
    }

    @PostMapping("/userapp/create")
    public String createUserApplication(Model model, @RequestParam String comment, @AuthenticationPrincipal SparkleUserDetails userDetails, HttpServletRequest request) throws UserNotFoundException, TooManyUserApplicationException {
        var usrApp = userappService.createUserAppForUser(userDetails.getUserId(), comment, InetAddress.ofLiteral(request.getRemoteAddr()));
        model.addAttribute("userapp", usrApp);
        return "userapp/created";
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserApplicationCreateRequest {
        private String comment;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserApplicationEditRequest {
        private String comment;
    }
}
