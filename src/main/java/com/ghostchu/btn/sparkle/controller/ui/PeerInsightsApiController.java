package com.ghostchu.btn.sparkle.controller.ui;

import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.BanPeerInsightsResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.PeerInsightsModels.SwarmPeerInsightsResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.PeerInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui/peer-insights")
@RequiredArgsConstructor
public class PeerInsightsApiController {
    private final PeerInsightsService peerInsightsService;

    @GetMapping("/swarm")
    public SwarmPeerInsightsResponse swarm(
            @RequestParam(defaultValue = "7") int days
    ) throws BusinessException {
        return peerInsightsService.swarmInsights(days);
    }

    @GetMapping("/ban")
    public BanPeerInsightsResponse ban(
            @RequestParam(defaultValue = "7") int days
    ) throws BusinessException {
        return peerInsightsService.banInsights(days);
    }
}
