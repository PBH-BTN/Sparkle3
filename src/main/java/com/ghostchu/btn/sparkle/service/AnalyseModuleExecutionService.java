package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.service.impl.allowlist.AnalyseBtnBypassServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseDatacenterHighRiskServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseGopeedDevIdentityServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseMassDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRain000IdentityServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRandomIdentityServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRuleConcurrentDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRuleOverDownloadServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.denylist.AnalyseRuleUnTrustVoteServiceImpl;
import com.ghostchu.btn.sparkle.service.GeoIpCityBlacklistService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyseModuleExecutionService {
    private final Map<String, Runnable> runners = new LinkedHashMap<>();

    public AnalyseModuleExecutionService(
            AnalyseRuleUnTrustVoteServiceImpl untrustedVote,
            AnalyseRuleOverDownloadServiceImpl overDownload,
            AnalyseRuleConcurrentDownloadServiceImpl concurrentDownload,
            AnalyseMassDownloadServiceImpl massDownload,
            AnalyseRandomIdentityServiceImpl randomIdentity,
            AnalyseRain000IdentityServiceImpl rain000Identity,
            AnalyseGopeedDevIdentityServiceImpl gopeedDevIdentity,
            AnalyseDatacenterHighRiskServiceImpl datacenterHighRisk,
            GeoIpCityBlacklistService geoIpCityBlacklist,
            AnalyseBtnBypassServiceImpl btnBypass
    ) {
        runners.put("untrusted-vote", untrustedVote::analyseUntrusted);
        runners.put("overdownload-analyse", overDownload::analyseOverDownload);
        runners.put("concurrent-download-analyse", concurrentDownload::analyseConcurrentDownload);
        runners.put("mass-download-monitor", massDownload::analyseMassDownload);
        runners.put("random-identity-analyse", randomIdentity::analyseRandomIdentity);
        runners.put("rain000-identity-analyse", rain000Identity::analyseRain000Identity);
        runners.put("gopeeddev-identity-analyse", gopeedDevIdentity::analyseGopeedDevIdentity);
        runners.put("datacenter-highrisk-analyse", datacenterHighRisk::analyseDatacenterHighRisk);
        runners.put("geoip-city-blacklist", geoIpCityBlacklist::generateScheduled);
        runners.put("btnbypass-userapps", btnBypass::analyseBtnBypass);
    }

    public @NotNull Set<String> moduleIds() {
        return Set.copyOf(runners.keySet());
    }

    public @Nullable Runnable runner(String moduleId) {
        return runners.get(moduleId);
    }
}
