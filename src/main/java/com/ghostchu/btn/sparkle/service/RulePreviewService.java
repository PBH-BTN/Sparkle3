package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewEntry;
import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.RulePreviewModels.RulePreviewSection;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RulePreviewService {
    private static final List<String> PEER_RULE_TYPES = List.of(
            "peer_id",
            "client_name",
            "port",
            "script"
    );

    private final List<IPDenyListRuleProvider> denyListProviders;
    private final List<IPAllowListRuleProvider> allowListProviders;
    private final IRuleService ruleService;

    public @NotNull RulePreviewResponse preview() {
        List<RulePreviewSection> sections = new ArrayList<>();
        sections.add(new RulePreviewSection(
                "ip_denylist",
                providerEntries(denyListProviders)
        ));
        sections.add(new RulePreviewSection(
                "ip_allowlist",
                providerEntries(allowListProviders)
        ));

        List<Rule> peerRules = ruleService.list(new QueryWrapper<Rule>()
                .in("type", PEER_RULE_TYPES)
                .orderByAsc("type", "category", "id"));
        Map<String, List<RulePreviewEntry>> peerEntries = new LinkedHashMap<>();
        for (String type : PEER_RULE_TYPES) {
            peerEntries.put(type, new ArrayList<>());
        }
        for (Rule rule : peerRules) {
            peerEntries.computeIfAbsent(rule.getType(), ignored -> new ArrayList<>())
                    .add(new RulePreviewEntry(
                            "manual",
                            rule.getCategory(),
                            rule.getContent()
                    ));
        }
        for (String type : PEER_RULE_TYPES) {
            sections.add(new RulePreviewSection(
                    type,
                    List.copyOf(peerEntries.getOrDefault(type, List.of()))
            ));
        }
        return new RulePreviewResponse(List.copyOf(sections), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private List<RulePreviewEntry> providerEntries(List<? extends Object> providers) {
        return providers.stream()
                .sorted(Comparator.comparing(this::providerSource))
                .map(provider -> {
                    try {
                        String content;
                        if (provider instanceof IPDenyListRuleProvider denyProvider) {
                            content = denyProvider.getContent();
                        } else {
                            content = ((IPAllowListRuleProvider) provider).getContent();
                        }
                        if (content == null || content.isBlank()) {
                            return null;
                        }
                        return new RulePreviewEntry(providerSource(provider), null, content);
                    } catch (Exception exception) {
                        log.warn("Unable to load rule preview provider {}: {}",
                                provider.getClass().getSimpleName(),
                                exception.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String providerSource(Object provider) {
        String simpleName = provider.getClass().getSimpleName();
        return switch (simpleName) {
            case "RuleServiceImpl" -> "manual";
            case "AnalyseRuleOverDownloadServiceImpl" -> "overdownload";
            case "AnalyseRuleUnTrustVoteServiceImpl" -> "untrusted-vote";
            case "AnalyseRuleConcurrentDownloadServiceImpl" -> "concurrent-download";
            case "AnalyseRandomIdentityServiceImpl" -> "random-identity";
            case "AnalyseRain000IdentityServiceImpl" -> "rain000-identity";
            case "AnalyseGopeedDevIdentityServiceImpl" -> "gopeeddev-identity";
            case "AnalyseDatacenterHighRiskServiceImpl" -> "datacenter-high-risk";
            case "AnalyseBtnBypassServiceImpl" -> "btn-bypass";
            default -> simpleName;
        };
    }
}
