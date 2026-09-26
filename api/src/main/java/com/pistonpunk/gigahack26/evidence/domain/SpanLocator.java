package com.pistonpunk.gigahack26.evidence.domain;

import java.util.Optional;

public final class SpanLocator {

    public record Span(int start, int end, boolean exact) {
        public int length() {
            return end - start;
        }
    }

    private SpanLocator() {
    }

    public static Optional<Span> locate(String document, String quote) {
        if (document == null || quote == null || quote.isBlank()) {
            return Optional.empty();
        }

        NormalizedText doc = NormalizedText.of(document);
        NormalizedText needle = NormalizedText.of(quote);
        if (needle.text().isBlank()) {
            return Optional.empty();
        }

        int index = doc.indexOf(needle.text());
        if (index >= 0) {
            return Optional.of(toSpan(doc, index, needle.text().length(), true));
        }

        String trimmed = trimToSentence(needle.text());
        if (!trimmed.equals(needle.text()) && !trimmed.isBlank()) {
            index = doc.indexOf(trimmed);
            if (index >= 0) {
                return Optional.of(toSpan(doc, index, trimmed.length(), true));
            }
        }

        return fuzzy(document, doc, needle.text());
    }

    public static boolean contains(String document, int start, int end, String quote) {
        if (document == null || quote == null || start < 0 || end > document.length() || start >= end) {
            return false;
        }
        NormalizedText window = NormalizedText.of(document.substring(start, end));
        NormalizedText needle = NormalizedText.of(quote);
        return !needle.text().isBlank() && window.contains(needle.text());
    }

    private static Span toSpan(NormalizedText doc, int normalizedStart, int normalizedLength, boolean exact) {
        int start = doc.originalStart(normalizedStart);
        int end = doc.originalEnd(normalizedStart + normalizedLength);
        return new Span(Math.max(0, start), Math.max(start + 1, end), exact);
    }

    private static String trimToSentence(String normalized) {
        int cut = normalized.lastIndexOf(". ");
        if (cut > 20) {
            return normalized.substring(0, cut + 1);
        }
        return normalized;
    }

    private static Optional<Span> fuzzy(String document, NormalizedText doc, String needle) {
        String[] tokens = needle.split(" ");
        if (tokens.length < 4) {
            return Optional.empty();
        }

        int bestStart = -1;
        int bestHits = 0;
        int windowSize = tokens.length;

        String[] docTokens = doc.text().split(" ");
        for (int i = 0; i + windowSize <= docTokens.length; i++) {
            int hits = 0;
            for (int j = 0; j < windowSize; j++) {
                if (docTokens[i + j].equals(tokens[j])) {
                    hits++;
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                bestStart = i;
            }
        }

        if (bestStart < 0 || bestHits < Math.ceil(windowSize * 0.9)) {
            return Optional.empty();
        }

        int normalizedStart = 0;
        for (int i = 0; i < bestStart; i++) {
            normalizedStart += docTokens[i].length() + 1;
        }
        int normalizedEnd = normalizedStart;
        for (int i = 0; i < windowSize; i++) {
            normalizedEnd += docTokens[bestStart + i].length() + 1;
        }
        return Optional.of(toSpan(doc, normalizedStart, Math.max(1, normalizedEnd - normalizedStart - 1), false));
    }
}
