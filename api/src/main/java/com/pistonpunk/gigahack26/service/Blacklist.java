package com.pistonpunk.gigahack26.service;

import java.text.Normalizer;
import java.util.Optional;
import java.util.Set;

/**
 * The operator's own exclusion list, held in the settings row rather than in the
 * shipped vendor registry, so a company can be kept out of the queue without a
 * code change and a deploy.
 *
 * An entry matches a company on its domain or on its whole name, folded and with
 * the legal suffix dropped, so "Orange" does not take out "Orange Systems" by
 * accident and "Komerční banka" is reachable as "Komercni banka".
 */
public final class Blacklist {

    private Blacklist() {
    }

    public static Optional<String> match(String list, String name, String domain) {
        if (list == null || list.isBlank()) {
            return Optional.empty();
        }
        String foldedDomain = fold(domain);
        String foldedName = fold(name);
        for (String raw : list.split("[,\\n\\r;]+")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String needle = fold(entry);
            if (needle.isEmpty()) {
                continue;
            }
            if (!foldedDomain.isEmpty() && foldedDomain.equals(needle)) {
                return Optional.of(entry);
            }
            if (bare(foldedName).equals(bare(needle))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static String bare(String folded) {
        String value = folded;
        while (true) {
            int cut = value.lastIndexOf(' ');
            if (cut < 0) {
                return value;
            }
            String tail = value.substring(cut + 1);
            if (!SUFFIXES.contains(tail)) {
                return value;
            }
            value = value.substring(0, cut);
        }
    }

    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean gap = false;
        for (char raw : decomposed.toCharArray()) {
            char c = Character.toLowerCase(raw);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                gap = false;
            } else if (c == '&') {
                out.append(" and ");
                gap = false;
            } else if (!gap && out.length() > 0) {
                out.append(' ');
                gap = true;
            }
        }
        return out.toString().trim();
    }

    private static final Set<String> SUFFIXES = Set.of(
            "inc", "incorporated", "ltd", "limited", "plc", "llc", "corp", "corporation",
            "co", "company", "ag", "se", "sa", "nv", "bv", "gmbh", "ab", "asa", "oyj",
            "oy", "spa", "srl", "pte", "kk", "aps", "holding", "holdings", "group",
            "uk", "us", "usa", "de", "fr", "nl", "dk", "no", "fi", "ie", "it",
            "es", "pl", "ro", "md", "ch", "at", "be", "ca", "eu", "emea", "international",
            "global", "europe", "deutschland", "sas", "sl", "as", "sro");
}
