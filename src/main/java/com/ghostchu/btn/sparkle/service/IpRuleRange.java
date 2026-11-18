package com.ghostchu.btn.sparkle.service;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.math.BigInteger;

record IpRuleRange(IPAddress.IPVersion version, BigInteger lower, BigInteger upper) {
    static IpRuleRange parse(String rawLine) {
        int comment = rawLine.indexOf('#');
        String line = (comment < 0 ? rawLine : rawLine.substring(0, comment)).strip();
        if (line.isEmpty()) {
            return null;
        }
        if (line.indexOf(',') >= 0) {
            String[] fields = line.split(",", 3);
            try {
                if (Integer.parseInt(fields[1].strip()) >= 128) {
                    return null;
                }
            } catch (NumberFormatException exception) {
                return null;
            }
            line = fields[0].strip();
        }

        IPAddress first = parseAddress(line);
        if (first != null) {
            return range(first, first);
        }
        int dash = line.indexOf('-');
        return dash < 0 ? null : range(parseAddress(line.substring(0, dash)), parseAddress(line.substring(dash + 1)));
    }

    static IPAddress parseAddress(String value) {
        IPAddress address = new IPAddressString(value.strip()).getAddress();
        if (address != null && address.isIPv4Convertible()) {
            address = address.toIPv4();
        }
        return address != null && address.getPrefixLength() != null ? address.toPrefixBlock() : address;
    }

    private static IpRuleRange range(IPAddress first, IPAddress last) {
        if (first == null || last == null || first.getIPVersion() != last.getIPVersion()) {
            return null;
        }
        BigInteger lower = first.getLower().getValue();
        BigInteger upper = last.getUpper().getValue();
        return lower.compareTo(upper) <= 0 ? new IpRuleRange(first.getIPVersion(), lower, upper) : null;
    }

    boolean overlaps(IPAddress network) {
        return network.getIPVersion() == version
                && network.getLower().getValue().compareTo(upper) <= 0
                && network.getUpper().getValue().compareTo(lower) >= 0;
    }
}
