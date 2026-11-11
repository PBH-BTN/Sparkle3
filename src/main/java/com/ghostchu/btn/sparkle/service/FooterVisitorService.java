package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.ProxyProvider;
import com.ghostchu.btn.sparkle.controller.ui.dto.FooterModels.VisitorView;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FooterVisitorService {
    private static final Pattern EDGE_VERSION = Pattern.compile("Edg(?:e|A|iOS)?/([\\d.]+)");
    private static final Pattern OPERA_VERSION = Pattern.compile("(?:OPR|Opera)/([\\d.]+)");
    private static final Pattern CHROME_VERSION = Pattern.compile("(?:Chrome|CriOS)/([\\d.]+)");
    private static final Pattern FIREFOX_VERSION = Pattern.compile("(?:Firefox|FxiOS)/([\\d.]+)");
    private static final Pattern SAFARI_VERSION = Pattern.compile("Version/([\\d.]+)");
    private static final Pattern ANDROID_VERSION = Pattern.compile("Android\\s+([\\d.]+)");
    private static final Pattern IOS_VERSION = Pattern.compile("(?:OS|CPU OS)\\s+([\\d_]+)");
    private static final Pattern MAC_VERSION = Pattern.compile("Mac OS X\\s+([\\d_]+)");

    private final IPDBManager ipdbManager;

    public @NotNull VisitorView visitor(@NotNull HttpServletRequest request) {
        String ip = clientIp(request);
        String userAgent = request.getHeader("User-Agent");
        return new VisitorView(
                ip,
                geo(ip),
                operatingSystem(userAgent, request.getHeader("Sec-CH-UA-Platform")),
                browser(userAgent),
                proxyProvider(request)
        );
    }

    private String clientIp(HttpServletRequest request) {
        String[] trustedHeaders = {
                "CF-Connecting-IP",
                "EO-Connecting-IP",
                "EO-Client-IP",
                "EO-Real-IP",
                "True-Client-IP"
        };
        for (String header : trustedHeaders) {
            String candidate = firstValidIp(request.getHeader(header));
            if (candidate != null) return candidate;
        }
        String forwarded = firstValidIp(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) return forwarded;
        String realIp = firstValidIp(request.getHeader("X-Real-IP"));
        if (realIp != null) return realIp;
        String remote = firstValidIp(request.getRemoteAddr());
        return remote == null ? request.getRemoteAddr() : remote;
    }

    private @Nullable String firstValidIp(@Nullable String header) {
        if (header == null || header.isBlank()) return null;
        for (String part : header.split(",")) {
            String candidate = part.trim();
            if (candidate.startsWith("[") && candidate.contains("]")) {
                candidate = candidate.substring(1, candidate.indexOf(']'));
            }
            try {
                InetAddress.ofLiteral(candidate);
                return candidate;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private @Nullable String geo(String ip) {
        try {
            var response = ipdbManager.queryIPDB(InetAddress.ofLiteral(ip));
            IPGeoData data = response.geoData() == null ? null : response.geoData().get();
            if (data == null) return null;

            Set<String> parts = new LinkedHashSet<>();
            if (data.getCountry() != null) {
                String country = data.getCountry().getIso();
                if (country == null || country.isBlank()) {
                    country = data.getCountry().getName();
                }
                if (country != null && !country.isBlank()) parts.add(country);
            }
            if (data.getCity() != null) {
                if (data.getCity().getCnProvince() != null
                        && !data.getCity().getCnProvince().isBlank()) {
                    parts.add(data.getCity().getCnProvince());
                }
                if (data.getCity().getCnCity() != null
                        && !data.getCity().getCnCity().isBlank()) {
                    parts.add(data.getCity().getCnCity());
                } else if (data.getCity().getName() != null
                        && !data.getCity().getName().isBlank()) {
                    parts.add(data.getCity().getName());
                }
            }
            return parts.isEmpty() ? null : String.join(", ", parts);
        } catch (Exception exception) {
            return null;
        }
    }

    private @Nullable String operatingSystem(@Nullable String userAgent,
                                             @Nullable String clientHintPlatform) {
        String ua = userAgent == null ? "" : userAgent;
        Matcher android = ANDROID_VERSION.matcher(ua);
        if (android.find()) {
            return "Android " + version(android.group(1));
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            Matcher ios = IOS_VERSION.matcher(ua);
            return ios.find() ? "iOS " + version(ios.group(1)) : "iOS";
        }
        Matcher mac = MAC_VERSION.matcher(ua);
        if (mac.find()) {
            return "macOS " + version(mac.group(1));
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("FreeBSD")) {
            return "FreeBSD";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        String hint = clientHintPlatform == null ? "" : clientHintPlatform.replace("\"", "").trim();
        return hint.isBlank() ? null : hint;
    }

    private @Nullable String browser(@Nullable String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        Matcher edge = EDGE_VERSION.matcher(userAgent);
        if (edge.find()) return "Edge " + version(edge.group(1));
        Matcher opera = OPERA_VERSION.matcher(userAgent);
        if (opera.find()) return "Opera " + version(opera.group(1));
        Matcher chrome = CHROME_VERSION.matcher(userAgent);
        if (chrome.find()) return "Chrome " + version(chrome.group(1));
        Matcher firefox = FIREFOX_VERSION.matcher(userAgent);
        if (firefox.find()) return "Firefox " + version(firefox.group(1));
        Matcher safari = SAFARI_VERSION.matcher(userAgent);
        if (safari.find() && userAgent.contains("Safari")) {
            return "Safari " + version(safari.group(1));
        }
        return null;
    }

    private String version(String value) {
        return value.replace('_', '.').split("\\.")[0];
    }

    private @Nullable ProxyProvider proxyProvider(HttpServletRequest request) {
        if (headerPresent(request, "CF-Connecting-IP")
                || headerPresent(request, "CF-Ray")
                || headerPresent(request, "CF-Visitor")) {
            return ProxyProvider.CLOUDFLARE;
        }
        if (headerPresent(request, "EO-Connecting-IP")
                || headerPresent(request, "EO-Client-IP")
                || headerPresent(request, "EO-Real-IP")
                || headerPresent(request, "EO-Ray")
                || headerPresent(request, "EO-Page-Rule")
                || headerPresent(request, "X-EdgeOne-Client-IP")
                || containsHeaderValue(request, "Via", "tencent")
                || containsHeaderValue(request, "Via", "edgeone")
                || containsHeaderValue(request, "Server", "edgeone")) {
            return ProxyProvider.TENCENT_EDGEONE;
        }
        return null;
    }

    private boolean headerPresent(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank();
    }

    private boolean containsHeaderValue(HttpServletRequest request,
                                        String name,
                                        String expected) {
        String value = request.getHeader(name);
        return value != null && value.toLowerCase().contains(expected);
    }
}
