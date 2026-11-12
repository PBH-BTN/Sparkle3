package com.ghostchu.btn.sparkle.service.ipquery;

import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentAccess;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentScopeOption;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.GeoView;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.LookupResponse;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.ai.AiModerationService;
import com.ghostchu.btn.sparkle.service.impl.QueryIpServiceImpl;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IpQueryLookupService {
    private final QueryIpServiceImpl queryIpService;
    private final IPDBManager geoIpManager;
    private final IpQuerySettingsService settingsService;
    private final AiModerationService moderationService;

    public LookupResponse lookup(String value) throws BusinessException {
        IpScope scope = IpScope.parse(value);
        int commentPrefix = scope.defaultCommentPrefix();
        IpScope commentScope = scope.withCommentPrefix(commentPrefix);
        GeoView geoStart = geo(scope.network().getLower().toInetAddress());
        GeoView geoEnd = geo(scope.network().getUpper().toInetAddress());
        CommentAccess access = access();
        List<CommentScopeOption> commentScopes = new ArrayList<>();
        for (Integer prefix : IpScope.supportedPrefixes(scope.version())) {
            commentScopes.add(new CommentScopeOption(
                    prefix,
                    scope.withCommentPrefix(prefix).canonicalCidr(),
                    prefix == commentPrefix
            ));
        }
        return new LookupResponse(
                scope.input(),
                scope.normalizedAddress(),
                scope.canonicalCidr(),
                scope.scopeStart(),
                scope.scopeStart(),
                scope.scopeEnd(),
                scope.version(),
                scope.prefixLength(),
                scope.totalAddresses(),
                scope.expandedAddress(),
                scope.binaryAddress(),
                scope.decimalAddress(),
                scope.reverseDnsName(),
                scope.flags(),
                scope.globallyRoutable(),
                commentScope.canonicalCidr(),
                commentPrefix,
                List.copyOf(commentScopes),
                geoStart,
                geoEnd,
                queryIpService.queryIp(scope.canonicalCidr()),
                access,
                OffsetDateTime.now()
        );
    }

    public CommentAccess access() {
        var settings = settingsService.get();
        return new CommentAccess(
                settings.commentsVisible(),
                settings.commentsVisible() && settings.commentsEnabled(),
                moderationService.enabled(),
                settings.maxLength(),
                settings.hourlyLimit()
        );
    }

    private GeoView geo(InetAddress address) {
        try {
            IPGeoData data = geoIpManager.queryIPDB(address).geoData().get();
            if (data == null) {
                return null;
            }
            IPGeoData.ASData as = data.getAs();
            IPGeoData.ASData.ASNetwork network = as == null ? null : as.getNetwork();
            return new GeoView(
                    data.getCountry() == null ? null : data.getCountry().getName(),
                    data.getCountry() == null ? null : data.getCountry().getIso(),
                    data.getCity() == null ? null : data.getCity().getCnProvince(),
                    data.getCity() == null
                            ? null
                            : firstNonBlank(
                                    data.getCity().getCnCity(),
                                    data.getCity().getName()
                            ),
                    data.getCity() == null ? null : data.getCity().getCnDistricts(),
                    as == null || as.getNumber() == null
                            ? null
                            : "AS" + as.getNumber(),
                    as == null ? null : as.getOrganization(),
                    network == null ? null : network.getIpAddress()
                            + (network.getPrefixLength() == null
                            ? ""
                            : "/" + network.getPrefixLength()),
                    data.getNetwork() == null ? null : data.getNetwork().getIsp(),
                    data.getNetwork() == null
                            ? null
                            : data.getNetwork().getNetType()
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
