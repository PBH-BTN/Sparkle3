package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlainTextEmailSanitizerTest {
    @Test
    void removesHtmlTagsAndEntities() {
        assertEquals(
                "Hello",
                PlainTextEmailSanitizer.subject("<b>Hello</b> &lt;world&gt;")
        );
        assertEquals(
                "First\n\nsecond",
                PlainTextEmailSanitizer.content("<p>First</p>\n\n<script>second</script>")
        );
    }

    @Test
    void collapsesSubjectWhitespace() {
        assertEquals(
                "Hello world",
                PlainTextEmailSanitizer.subject(" Hello\n\tworld ")
        );
    }

    @Test
    void rejectsOversizedValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlainTextEmailSanitizer.subject("a".repeat(81))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlainTextEmailSanitizer.content("a".repeat(2_001))
        );
    }
}
