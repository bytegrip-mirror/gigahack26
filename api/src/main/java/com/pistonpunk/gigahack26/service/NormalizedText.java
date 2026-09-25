package com.pistonpunk.gigahack26.service;

import java.text.Normalizer;
import java.util.Locale;

public final class NormalizedText {

    private final String text;
    private final int[] origin;

    private NormalizedText(String text, int[] origin) {
        this.text = text;
        this.origin = origin;
    }

    public static NormalizedText of(String raw) {
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(decomposed.length());
        int[] origin = new int[decomposed.length()];
        boolean lastWasSpace = true;

        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            char mapped = switch (c) {
                case '‘', '’', '‛', 'ʼ' -> '\'';
                case '“', '”' -> '"';
                case '–', '—', '−' -> '-';
                case ' ', ' ', ' ' -> ' ';
                default -> c;
            };

            if (mapped == '​' || mapped == '﻿' || mapped == '­') {
                continue;
            }
            if (Character.isWhitespace(mapped)) {
                if (lastWasSpace) {
                    continue;
                }
                lastWasSpace = true;
                out.append(' ');
                origin[out.length() - 1] = i;
                continue;
            }
            lastWasSpace = false;
            out.append(Character.toLowerCase(mapped));
            origin[out.length() - 1] = i;
        }

        int length = out.length();
        while (length > 0 && out.charAt(length - 1) == ' ') {
            length--;
        }
        return new NormalizedText(out.substring(0, length), origin);
    }

    public String text() {
        return text;
    }

    public boolean contains(CharSequence needle) {
        return text.contains(needle);
    }

    public int indexOf(CharSequence needle) {
        return text.indexOf(needle.toString());
    }

    public int originalStart(int normalizedIndex) {
        if (normalizedIndex < 0 || normalizedIndex >= text.length()) {
            return -1;
        }
        return origin[normalizedIndex];
    }

    public int originalEnd(int normalizedIndexExclusive) {
        if (normalizedIndexExclusive <= 0) {
            return -1;
        }
        int clamped = Math.min(normalizedIndexExclusive, text.length());
        if (clamped < text.length()) {
            return origin[clamped];
        }
        return origin[text.length() - 1] + 1;
    }
}
