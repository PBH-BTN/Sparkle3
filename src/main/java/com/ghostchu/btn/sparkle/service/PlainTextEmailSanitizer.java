package com.ghostchu.btn.sparkle.service;

import java.util.regex.Pattern;

final class PlainTextEmailSanitizer {
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]*>");
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\r\\n\\t]]");
    private static final int MAX_SUBJECT_CODE_POINTS = 80;
    private static final int MAX_CONTENT_CODE_POINTS = 2_000;

    private PlainTextEmailSanitizer() {
    }

    static String subject(String value) {
        return requireMax(sanitize(value, false), MAX_SUBJECT_CODE_POINTS);
    }

    static String content(String value) {
        return requireMax(sanitize(value, true), MAX_CONTENT_CODE_POINTS);
    }

    private static String sanitize(String value, boolean multiline) {
        String text = value == null ? "" : value;
        text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = text.replace("<", " ").replace(">", " ");
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        if (multiline) {
            text = text.replace("\t", " ")
                    .replaceAll("[ ]+", " ")
                    .replaceAll(" *\\n *", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
        } else {
            text = text.replaceAll("\\s+", " ").trim();
        }
        return text;
    }

    private static String requireMax(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        throw new IllegalArgumentException("Text exceeds the allowed length");
    }
}
