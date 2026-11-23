package com.ghostchu.btn.sparkle.service.mail;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlainTextMailTemplate {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

    private PlainTextMailTemplate() {}

    public static String render(String template, Map<String, String> values) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
