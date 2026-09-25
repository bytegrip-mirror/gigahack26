package com.pistonpunk.gigahack26.service;

import java.util.ArrayList;
import java.util.List;

public final class TextWindows {

    public record Window(int start, int end, String text) {
    }

    private static final int SENTENCES_PER_WINDOW = 3;
    private static final int OVERLAP = 1;
    private static final int MIN_WINDOW_CHARS = 60;

    private TextWindows() {
    }

    public static List<Window> split(String document) {
        List<int[]> sentences = sentences(document);
        List<Window> windows = new ArrayList<>();
        if (sentences.isEmpty()) {
            return windows;
        }

        int step = Math.max(1, SENTENCES_PER_WINDOW - OVERLAP);
        for (int i = 0; i < sentences.size(); i += step) {
            int last = Math.min(sentences.size() - 1, i + SENTENCES_PER_WINDOW - 1);
            int start = sentences.get(i)[0];
            int end = sentences.get(last)[1];
            if (end - start >= MIN_WINDOW_CHARS) {
                windows.add(new Window(start, end, document.substring(start, end)));
            }
            if (last == sentences.size() - 1) {
                break;
            }
        }
        return windows;
    }

    private static List<int[]> sentences(String document) {
        List<int[]> spans = new ArrayList<>();
        int length = document.length();
        int start = 0;

        for (int i = 0; i < length; i++) {
            char c = document.charAt(i);
            if (c != '.' && c != '!' && c != '?') {
                continue;
            }
            int next = i + 1;
            while (next < length && (document.charAt(next) == '"' || document.charAt(next) == '\'')) {
                next++;
            }
            if (next < length && !Character.isWhitespace(document.charAt(next))) {
                continue;
            }
            addIfUseful(spans, document, start, next);
            start = next;
        }
        addIfUseful(spans, document, start, length);
        return spans;
    }

    private static void addIfUseful(List<int[]> spans, String document, int start, int end) {
        int trimmedStart = start;
        int trimmedEnd = end;
        while (trimmedStart < trimmedEnd && Character.isWhitespace(document.charAt(trimmedStart))) {
            trimmedStart++;
        }
        while (trimmedEnd > trimmedStart && Character.isWhitespace(document.charAt(trimmedEnd - 1))) {
            trimmedEnd--;
        }
        if (trimmedEnd - trimmedStart >= 20) {
            spans.add(new int[]{trimmedStart, trimmedEnd});
        }
    }
}
