package com.ghostchu.btn.sparkle.service.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.XZInputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XzCsvWriterTest {
    @TempDir
    Path directory;

    @Test
    void completesTheRecordThatCrossesTheCompressedLimit() throws Exception {
        var columns = List.of("description", "structured_data");
        List<Map<String, Object>> records = List.of(
                Map.of("description", "=1+2", "structured_data", "{\"port\":1234}"),
                Map.of("description", "second,\nrow", "structured_data", "{\"flags\":[1,2]}"),
                Map.of("description", "third"));
        var first = directory.resolve("first.xz");
        var firstOnly = XzCsvWriter.write(first, columns, records, 1, Long.MAX_VALUE, () -> {}, Object::toString);
        long threshold = firstOnly.bytes() + 1;
        var truncated = directory.resolve("truncated.xz");
        var result = XzCsvWriter.write(truncated, columns, records, 100, threshold, () -> {}, Object::toString);
        assertEquals(2, result.rows());
        assertEquals(Files.size(truncated), result.bytes());
        assertTrue(result.bytes() >= threshold);
        assertTrue(result.truncated());
        try (var input = new XZInputStream(Files.newInputStream(truncated))) {
            String csv = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("\"description\",\"structured_data\"\r\n\"'=1+2\",\"{\"\"port\"\":1234}\"\r\n"
                    + "\"second,\nrow\",\"{\"\"flags\"\":[1,2]}\"\r\n", csv);
        }
    }

    @Test
    void writesHeaderOnlyForEmptyResults() throws Exception {
        var file = directory.resolve("empty.xz");
        var result = XzCsvWriter.write(file, List.of("day"), List.of(), 10, 1024, () -> {}, Object::toString);
        assertEquals(0, result.rows());
        assertFalse(result.truncated());
        try (var input = new XZInputStream(Files.newInputStream(file))) {
            assertEquals("\"day\"\r\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
