package com.ghostchu.btn.sparkle.service.moderation;

import java.util.TreeSet;

/** Immutable Aho–Corasick automaton over Java String code units. */
final class SensitiveWordMatcher {
    private final int[] offsets;
    private final char[] labels;
    private final int[] targets;
    private final int[] failures;
    private final boolean[] matches;

    private SensitiveWordMatcher(int[] offsets, char[] labels, int[] targets, int[] failures, boolean[] matches) {
        this.offsets = offsets;
        this.labels = labels;
        this.targets = targets;
        this.failures = failures;
        this.matches = matches;
    }

    static SensitiveWordMatcher compile(String text) {
        var words = new TreeSet<String>();
        text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(words::add);

        int capacity = 1 + words.stream().mapToInt(String::length).sum();
        int[] parents = new int[capacity];
        char[] incoming = new char[capacity];
        boolean[] matches = new boolean[capacity];
        int[] path = new int[capacity];
        int states = 1;
        String previous = "";
        for (String word : words) {
            int prefix = 0;
            while (prefix < previous.length() && prefix < word.length()
                    && previous.charAt(prefix) == word.charAt(prefix)) {
                prefix++;
            }
            for (int i = prefix; i < word.length(); i++) {
                parents[states] = path[i];
                incoming[states] = word.charAt(i);
                path[i + 1] = states++;
            }
            matches[path[word.length()]] = true;
            previous = word;
        }

        int[] offsets = new int[states + 1];
        for (int node = 1; node < states; node++) {
            offsets[parents[node] + 1]++;
        }
        for (int i = 1; i <= states; i++) {
            offsets[i] += offsets[i - 1];
        }
        int[] cursor = offsets.clone();
        char[] labels = new char[states - 1];
        int[] targets = new int[states - 1];
        // Sorted input creates each parent's outgoing edges in character order.
        for (int node = 1; node < states; node++) {
            int edge = cursor[parents[node]]++;
            labels[edge] = incoming[node];
            targets[edge] = node;
        }

        int[] failures = new int[states];
        var matcher = new SensitiveWordMatcher(offsets, labels, targets, failures,
                java.util.Arrays.copyOf(matches, states));
        int[] queue = new int[states];
        int tail = 0;
        for (int edge = 0; edge < offsets[1]; edge++) {
            queue[tail++] = targets[edge];
        }
        for (int head = 0; head < tail; head++) {
            int state = queue[head];
            for (int edge = offsets[state]; edge < offsets[state + 1]; edge++) {
                int target = targets[edge];
                int fallback = matcher.advance(failures[state], labels[edge]);
                failures[target] = fallback;
                matcher.matches[target] |= matcher.matches[fallback];
                queue[tail++] = target;
            }
        }
        return matcher;
    }

    boolean isEmpty() {
        return labels.length == 0;
    }

    boolean contains(String text) {
        if (text == null) {
            return false;
        }
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            state = advance(state, text.charAt(i));
            if (matches[state]) {
                return true;
            }
        }
        return false;
    }

    private int advance(int state, char character) {
        while (true) {
            int low = offsets[state];
            int high = offsets[state + 1] - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                char label = labels[middle];
                if (label == character) {
                    return targets[middle];
                }
                if (label < character) {
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            if (state == 0) {
                return 0;
            }
            state = failures[state];
        }
    }
}
