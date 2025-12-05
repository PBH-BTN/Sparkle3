package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.InetAddress;

@Controller
public class PingQueryIpIframeController extends BasePingController {

    @Autowired
    private QueryIpServiceImpl queryIpService;

    @GetMapping("/ping/queryIp/widget")
    //@Cacheable(value = "pingQueryIpCache#600000", key = "#ip", unless = "#result == null || !#result.statusCode.is2xxSuccessful()")
    public @NotNull String queryIp(@RequestParam String ip, Model model) throws AccessDeniedException, UserApplicationBannedException, UserApplicationNotFoundException {
        verifyUserApplication();
        var peerIp= InetAddress.ofLiteral(ip);
        var result = queryIpService.queryIp(peerIp);
        model.addAttribute("result", result);
        model.addAttribute("ip", ip);
        return "widget/queryIp";
    }


}
