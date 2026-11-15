package com.ghostchu.btn.sparkle.service.moderation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveWordMatcherTest {
    @Test
    void handlesSuffixesCommentsAndSupplementaryCharacters() {
        var matcher = SensitiveWordMatcher.compile("  #コメント\r\n\n 黑猫 \n猫砂\n黑猫咪\n𠮷野\n猫砂\n");
        assertTrue(matcher.contains("一只黑猫"));
        assertTrue(matcher.contains("新的猫砂"));
        assertTrue(matcher.contains("𠮷野町"));
        assertFalse(matcher.contains("コメント"));
        assertFalse(matcher.contains("黑 狗"));
        assertFalse(matcher.contains(null));
    }

    @Test
    void agreesWithLiteralSearchAcrossSharedPrefixesAndSuffixes() {
        var random = new Random(47);
        List<String> words = IntStream.range(0, 120).mapToObj(i -> randomText(random, 3 + random.nextInt(7))).toList();
        var matcher = SensitiveWordMatcher.compile(String.join("\n", words));
        for (int i = 0; i < 1_000; i++) {
            String candidate = randomText(random, 35);
            assertEquals(words.stream().anyMatch(candidate::contains), matcher.contains(candidate), candidate);
        }
    }

    @Test
    void sharesLargeCjkDictionaryAcrossConcurrentBatches() throws Exception {
        String words = String.join("\n", IntStream.range(0, 100_000)
                .mapToObj(i -> "测试" + (char) ('一' + i / 400) + (char) ('一' + i % 400))
                .toList());
        var matcher = SensitiveWordMatcher.compile(words);
        var batches = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 32; i++) {
            batches.add(() -> {
                int hits = 0;
                for (int record = 0; record < 3_000; record++) {
                    if (matcher.contains("在线客户端") || matcher.contains("正常描述") || matcher.contains("下载器"))
                        hits++;
                    if (matcher.contains("数据内容测试一一"))
                        hits++;
                }
                return hits;
            });
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(batches)) {
                assertEquals(3_000, result.get());
            }
        }
    }

    private String randomText(Random random, int length) {
        return IntStream.range(0, length).map(i -> '一' + random.nextInt(9))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
