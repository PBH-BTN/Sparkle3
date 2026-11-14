package com.ghostchu.btn.sparkle.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

final class AfdianRemarkEmail {
    private static final Pattern CANDIDATE = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9][A-Za-z0-9.-]*");

    private AfdianRemarkEmail() { }

    static String extract(String remark) {
        if (remark == null || remark.isBlank() || remark.length() > 16_384) return null;
        String exact = validate(remark.trim());
        if (exact != null) return exact;
        var addresses = new LinkedHashSet<String>();
        var matcher = CANDIDATE.matcher(remark);
        while (matcher.find()) {
            String candidate = matcher.group().replaceAll("\\.+$", "");
            String email = validate(candidate);
            if (email != null) addresses.add(email);
            if (addresses.size() > 1) return null;
        }
        return addresses.isEmpty() ? null : addresses.iterator().next();
    }

    private static String validate(String value) {
        if (value.length() > 254 || value.contains("\r") || value.contains("\n")
                || value.chars().anyMatch(character -> character > 127)) return null;
        try {
            InternetAddress[] addresses = InternetAddress.parse(value, true);
            if (addresses.length != 1 || addresses[0].getPersonal() != null
                    || !value.equals(addresses[0].getAddress()) || !value.contains("@")) return null;
            addresses[0].validate();
            String normalized = value.toLowerCase(Locale.ROOT);
            return normalized.endsWith("@sparkle.system") ? null : normalized;
        } catch (AddressException exception) {
            return null;
        }
    }
}
