package com.ghostchu.btn.sparkle.service.ipquery;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.springframework.http.HttpStatus;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record IpScope(
        String input,
        IPAddress address,
        IPAddress network,
        String normalizedAddress,
        String canonicalCidr,
        String scopeStart,
        String scopeEnd,
        int version,
        int prefixLength,
        int defaultCommentPrefix,
        int reviewPrefix,
        boolean cidrInput,
        List<String> flags,
        boolean globallyRoutable,
        String totalAddresses,
        String expandedAddress,
        String binaryAddress,
        String decimalAddress,
        String reverseDnsName
) {
    private static final List<Integer> IPV4_COMMENT_PREFIXES =
            List.of(32, 24, 16);
    private static final List<Integer> IPV6_COMMENT_PREFIXES =
            List.of(64, 60, 56, 52, 50, 48, 32);
    private static final int IPV4_MIN_QUERY_PREFIX = 8;
    private static final int IPV6_MIN_QUERY_PREFIX = 32;

    public static IpScope parse(String raw) throws BusinessException {
        if (raw == null || raw.isBlank()) {
            throw badRequest("An IP address or CIDR is required");
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.length() > 128 || value.contains("://")) {
            throw badRequest("A valid IP address or CIDR is required");
        }
        IPAddressString parsed = new IPAddressString(value);
        if (!parsed.isValid()) {
            throw badRequest("Invalid IP address or CIDR: " + value);
        }
        IPAddress address;
        try {
            address = parsed.toAddress();
        } catch (AddressStringException exception) {
            throw badRequest("Invalid IP address or CIDR: " + value);
        }
        if (address.isIPv4Convertible() && !address.isIPv4()) {
            address = address.toIPv4();
        }
        int version = address.isIPv4() ? 4 : 6;
        int totalBits = version == 4 ? 32 : 128;
        Integer parsedPrefix = address.getNetworkPrefixLength();
        int prefixLength = parsedPrefix == null ? totalBits : parsedPrefix;
        int minPrefix = version == 4
                ? IPV4_MIN_QUERY_PREFIX
                : IPV6_MIN_QUERY_PREFIX;
        if (prefixLength < minPrefix || prefixLength > totalBits) {
            throw badRequest(
                    "This query interface accepts IPv4 /"
                            + minPrefix + "–/32 and IPv6 /"
                            + minPrefix + "–/128 to keep database queries bounded"
            );
        }
        IPAddress network = address.toPrefixBlock(prefixLength);
        String start = host(network.getLower());
        String end = host(network.getUpper());
        int defaultCommentPrefix = parsedPrefix != null
                && supportedPrefixes(version).contains(parsedPrefix)
                ? parsedPrefix
                : version == 4 ? 24 : 56;
        int reviewPrefix = version == 4 ? 16 : 32;
        List<String> flags = classify(network);
        boolean globallyRoutable = version == 4
                ? !flags.contains("PRIVATE")
                && !flags.contains("LOOPBACK")
                && !flags.contains("LINK_LOCAL")
                && !flags.contains("MULTICAST")
                && !flags.contains("UNSPECIFIED")
                && !flags.contains("DOCUMENTATION")
                && !flags.contains("BENCHMARK")
                && !flags.contains("CGNAT")
                && !flags.contains("RESERVED")
                : !flags.contains("PRIVATE")
                && !flags.contains("LOOPBACK")
                && !flags.contains("LINK_LOCAL")
                && !flags.contains("MULTICAST")
                && !flags.contains("UNSPECIFIED")
                && !flags.contains("DOCUMENTATION")
                && !flags.contains("RESERVED");
        IPAddress hostAddress = address.withoutPrefixLength();
        InetAddress inetAddress = hostAddress.toInetAddress();
        String decimal = version == 4
                ? new BigInteger(1, inetAddress.getAddress()).toString()
                : null;
        return new IpScope(
                raw.trim(),
                address,
                network,
                host(address),
                start + "/" + prefixLength,
                start,
                end,
                version,
                prefixLength,
                defaultCommentPrefix,
                reviewPrefix,
                parsedPrefix != null,
                List.copyOf(flags),
                globallyRoutable,
                BigInteger.ONE.shiftLeft(totalBits - prefixLength).toString(),
                hostAddress.toFullString(),
                hostAddress.toBinaryString(),
                decimal,
                hostAddress.toReverseDNSLookupString()
        );
    }

    public static String scope(String raw, int prefixLength)
            throws BusinessException {
        IpScope parsed = parse(raw);
        return parsed.withCommentPrefix(prefixLength).canonicalCidr();
    }

    public static boolean isSupportedCommentPrefix(int version, int prefix) {
        return supportedPrefixes(version).contains(prefix);
    }

    public static List<Integer> supportedPrefixes(int version) {
        return version == 4 ? IPV4_COMMENT_PREFIXES : IPV6_COMMENT_PREFIXES;
    }

    public IpScope withCommentPrefix(int prefixLength) throws BusinessException {
        if (!isSupportedCommentPrefix(version, prefixLength)) {
            throw badRequest("Unsupported /" + prefixLength
                    + " comment prefix for IPv" + version);
        }
        IPAddress scoped = address.toPrefixBlock(prefixLength);
        return new IpScope(
                input,
                address,
                scoped,
                normalizedAddress,
                host(scoped.getLower()) + "/" + prefixLength,
                host(scoped.getLower()),
                host(scoped.getUpper()),
                version,
                this.prefixLength,
                defaultCommentPrefix,
                reviewPrefix,
                cidrInput,
                flags,
                globallyRoutable,
                BigInteger.ONE.shiftLeft(
                        (version == 4 ? 32 : 128) - prefixLength
                ).toString(),
                expandedAddress,
                binaryAddress,
                decimalAddress,
                reverseDnsName
        );
    }

    public String postgresValue() {
        return canonicalCidr;
    }

    private static List<String> classify(IPAddress address) {
        List<String> flags = new ArrayList<>();
        if (address.isLoopback()) flags.add("LOOPBACK");
        if (address.isLinkLocal()) flags.add("LINK_LOCAL");
        if (address.isMulticast()) flags.add("MULTICAST");
        if (address.isAnyLocal()) flags.add("UNSPECIFIED");
        if (within(address, "10.0.0.0/8")
                || within(address, "172.16.0.0/12")
                || within(address, "192.168.0.0/16")
                || within(address, "fc00::/7")) {
            flags.add("PRIVATE");
        }
        if (within(address, "100.64.0.0/10")) flags.add("CGNAT");
        if (within(address, "192.0.2.0/24")
                || within(address, "198.51.100.0/24")
                || within(address, "203.0.113.0/24")
                || within(address, "2001:db8::/32")) {
            flags.add("DOCUMENTATION");
        }
        if (within(address, "198.18.0.0/15")) flags.add("BENCHMARK");
        if (within(address, "0.0.0.0/8")
                || within(address, "127.0.0.0/8")
                || within(address, "169.254.0.0/16")
                || within(address, "224.0.0.0/4")
                || within(address, "240.0.0.0/4")
                || within(address, "::/128")
                || within(address, "::1/128")
                || within(address, "fe80::/10")
                || within(address, "ff00::/8")) {
            flags.add("RESERVED");
        }
        if (flags.isEmpty()) {
            flags.add("PUBLIC");
        }
        return flags;
    }

    private static boolean within(IPAddress address, String cidr) {
        IPAddress block = new IPAddressString(cidr).getAddress();
        return block != null && block.contains(address);
    }

    private static String host(IPAddress value) {
        return value.withoutPrefixLength().toCanonicalString();
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
