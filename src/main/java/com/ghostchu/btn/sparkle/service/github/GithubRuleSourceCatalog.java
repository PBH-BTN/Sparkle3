package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GithubRuleSourceCatalog {
    public record Definition(
            String id,
            String beanName,
            String kind,
            String defaultFileName
    ) {
    }

    public record Content(
            String sourceId,
            @Nullable String version,
            @Nullable String content
    ) {
    }

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("manual-rules", "ruleServiceImpl", "DENY", "manual-rules.txt"),
            new Definition("untrusted-vote", "analyseRuleUnTrustVoteServiceImpl", "DENY",
                    "untrusted-ips.txt"),
            new Definition("overdownload", "analyseRuleOverDownloadServiceImpl", "DENY",
                    "overdownload-ips.txt"),
            new Definition("concurrent-download", "analyseRuleConcurrentDownloadServiceImpl", "DENY",
                    "concurrent-downloads-ips.txt"),
            new Definition("mass-download", "analyseMassDownloadServiceImpl", "DENY",
                    "mass-download-ips.txt"),
            new Definition("random-identity", "analyseRandomIdentityServiceImpl", "DENY",
                    "random-identity.txt"),
            new Definition("rain000-identity", "analyseRain000IdentityServiceImpl", "DENY",
                    "rain0.0.0.txt"),
            new Definition("gopeeddev-identity", "analyseGopeedDevIdentityServiceImpl", "DENY",
                    "gopeeddev.txt"),
            new Definition("datacenter-highrisk", "analyseDatacenterHighRiskServiceImpl", "DENY",
                    "datacenter-highrisk.txt"),
            new Definition("geoip-city-blacklist", "geoIpCityBlacklistService", "DENY",
                    "geoip-city-blacklist.txt"),
            new Definition("btnbypass-userapps", "analyseBtnBypassServiceImpl", "ALLOW",
                    "btnbypass-allowlist.txt")
    );

    private final Map<String, Definition> definitionsById;
    private final Map<String, IPDenyListRuleProvider> denyProviders;
    private final Map<String, IPAllowListRuleProvider> allowProviders;

    public GithubRuleSourceCatalog(
            Map<String, IPDenyListRuleProvider> denyProviders,
            Map<String, IPAllowListRuleProvider> allowProviders
    ) {
        this.denyProviders = denyProviders;
        this.allowProviders = allowProviders;
        this.definitionsById = DEFINITIONS.stream().collect(Collectors.toUnmodifiableMap(
                Definition::id,
                value -> value
        ));
    }

    public List<Definition> definitions() {
        return DEFINITIONS;
    }

    public Set<String> sourceIds() {
        return definitionsById.keySet();
    }

    public boolean isAvailable(String sourceId) {
        Definition definition = definitionsById.get(sourceId);
        if (definition == null) {
            return false;
        }
        return "ALLOW".equals(definition.kind())
                ? allowProviders.containsKey(definition.beanName())
                : denyProviders.containsKey(definition.beanName());
    }

    public @Nullable Content read(String sourceId) {
        Definition definition = definitionsById.get(sourceId);
        if (definition == null) {
            return null;
        }
        if ("ALLOW".equals(definition.kind())) {
            IPAllowListRuleProvider provider = allowProviders.get(definition.beanName());
            return provider == null
                    ? null
                    : new Content(sourceId, provider.getVersion(), provider.getContent());
        }
        IPDenyListRuleProvider provider = denyProviders.get(definition.beanName());
        return provider == null
                ? null
                : new Content(sourceId, provider.getVersion(), provider.getContent());
    }

    public String combineContent(List<String> sourceIds) {
        Set<String> selected = Set.copyOf(sourceIds);
        StringBuilder builder = new StringBuilder();
        for (Definition definition : DEFINITIONS) {
            if (!selected.contains(definition.id())) {
                continue;
            }
            Content content = read(definition.id());
            if (content == null || content.content() == null || content.content().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(content.content().strip());
        }
        return builder.isEmpty() ? "" : builder.append('\n').toString();
    }

    public List<Definition> orderedDefinitions(List<String> sourceIds) {
        Set<String> selected = Set.copyOf(sourceIds);
        return DEFINITIONS.stream()
                .filter(definition -> selected.contains(definition.id()))
                .toList();
    }

    public Map<String, Definition> definitionsById() {
        return new LinkedHashMap<>(definitionsById);
    }
}
