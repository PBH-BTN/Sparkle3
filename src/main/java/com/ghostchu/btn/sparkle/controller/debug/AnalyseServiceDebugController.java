package com.ghostchu.btn.sparkle.controller.debug;

import com.ghostchu.btn.sparkle.service.impl.allowlist.AnalyseBtnBypassServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRuleConcurrentDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRuleOverDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRuleUnTrustVoteServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/debug/analyse")
public class AnalyseServiceDebugController {

    @Autowired
    private AnalyseRuleOverDownloadServiceImpl analyseRuleOverDownloadService;
    @Autowired
    private AnalyseRuleUnTrustVoteServiceImpl analyseRuleUnTrustVoteService;
    @Autowired
    private AnalyseRuleConcurrentDownloadServiceImpl analyseRuleConcurrentDownloadService;
    @Autowired
    private AnalyseBtnBypassServiceImpl analyseBtnBypassServiceImpl;

    @RequestMapping("/executeAnalyseOverDownload")
    @ResponseBody
    public String executeAnalyseOverDownload() {
        analyseRuleOverDownloadService.analyseOverDownload();
        return "OK!";
    }

    @RequestMapping("/executeAnalyseUnTrustVote")
    @ResponseBody
    public String executeAnalyseUnTrustVote() {
        analyseRuleUnTrustVoteService.analyseUntrusted();
        return "OK!";
    }

    @RequestMapping("/executeAnalyseConcurrentDownload")
    @ResponseBody
    public String executeAnalyseConcurrentDownload() {
        analyseRuleConcurrentDownloadService.analyseOverDownload();
        return "OK!";
    }

    @RequestMapping("/executeAnalyseBtnBypass")
    @ResponseBody
    public String executeAnalyseBtnBypassImpl() {
        analyseBtnBypassServiceImpl.analyseBtnBypass();
        return "OK!";
    }
}
