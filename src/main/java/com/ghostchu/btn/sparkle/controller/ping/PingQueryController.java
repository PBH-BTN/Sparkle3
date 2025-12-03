package com.ghostchu.btn.sparkle.controller.ping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PingQueryController extends BasePingController {

    public static class QueryResult {
        private List<String> labels;
        private Long recentBanCount;
        private Long recentSwarmObserveSeen;
        private Long recentSwarmObserveTotal;
        private Long recentConcurrentDownloads;
    }

}
