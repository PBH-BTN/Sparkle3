package com.ghostchu.btn.sparkle.controller.ui.banhistory;

import com.ghostchu.btn.sparkle.controller.ui.AbstractSparkleMVC;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Ban History View Controller
 * Handles the ban history page rendering
 */
@Controller
public class BanHistoryViewController extends AbstractSparkleMVC {

    @Autowired
    private IBanHistoryService banHistoryService;

    @GetMapping("/banhistory")
    public String banHistoryIndex(Model model) {
        // Get dropdown options
        model.addAttribute("peerIds", banHistoryService.getDistinctPeerIds());
        model.addAttribute("peerClientNames", banHistoryService.getDistinctPeerClientNames());
        model.addAttribute("moduleNames", banHistoryService.getDistinctModuleNames());
        
        return "banhistory/index";
    }
}
