package com.pistonpunk.gigahack26.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the people a document names, and what it says they do.
 *
 * Press writing introduces a person the same way every time — a full name, a
 * comma, then the job — so that is the only shape this looks for. It is
 * deliberately narrow: a name without a role beside it is a name this cannot
 * place, and a wrong name attached to a target account is worse than no name.
 */
public final class PersonExtractor {

    public record Found(String name, String role, int start, int end) {
    }

    private static final String ROLE_WORDS = String.join("|",
            "chief executive officer", "chief financial officer", "chief technology officer",
            "chief information officer", "chief information security officer", "chief operating officer",
            "chief data officer", "chief digital officer", "chief people officer", "chief marketing officer",
            "chief [a-z]+ officer",
            "managing director", "group chief executive", "general manager", "country manager",
            "head of [a-z][a-z &-]{2,40}", "director of [a-z][a-z &-]{2,40}",
            "vice president[ ,]*(?:of )?[a-z][a-z &-]{0,40}", "senior vice president[ ,]*(?:of )?[a-z][a-z &-]{0,40}",
            "svp", "evp", "vp of [a-z][a-z &-]{2,40}", "ceo", "cfo", "cto", "cio", "ciso", "coo", "cdo",
            "president of [a-z][a-z &-]{2,40}", "chair of [a-z][a-z &-]{2,40}", "chief executive");

    private static final Pattern ROLE = Pattern.compile("\\b(" + ROLE_WORDS + ")\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_BEFORE = Pattern.compile(
            "([A-Z][\\p{L}'’.-]+(?:\\s+[A-Z][\\p{L}'’.-]+){1,2})\\s*,\\s*(?:the\\s+)?$");

    private static final Pattern NAME_AFTER = Pattern.compile(
            "^\\s*(?:is|was|,)?\\s*(?:the\\s+)?(?:company's\\s+|group's\\s+)?\\s*([A-Z][\\p{L}'’.-]+(?:\\s+[A-Z][\\p{L}'’.-]+){1,2})\\b");

    /**
     * Words that start a sentence or a clause and are capitalised for that reason
     * rather than because they are somebody's first name. "Later Ana Silva, chief
     * executive officer" otherwise reads as a person called Later Ana Silva.
     */
    private static final java.util.Set<String> NOT_A_FIRST_NAME = java.util.Set.of(
            "later", "meanwhile", "however", "according", "the", "in", "on", "at", "also", "and", "but",
            "said", "says", "he", "she", "they", "it", "its", "his", "her", "their", "this", "that",
            "there", "then", "now", "today", "yesterday", "last", "next", "after", "before", "as",
            "mr", "mrs", "ms", "dr", "prof", "sir", "dame");

    private static final int LOOK_BEHIND = 90;
    private static final int LOOK_AHEAD = 90;
    private static final int MAX_ROLE = 60;

    private PersonExtractor() {
    }

    public static List<Found> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, Found> found = new LinkedHashMap<>();
        Matcher roles = ROLE.matcher(text);
        while (roles.find()) {
            String role = tidy(roles.group(1));
            if (role == null) {
                continue;
            }
            int from = Math.max(0, roles.start() - LOOK_BEHIND);
            String before = text.substring(from, roles.start());
            Matcher name = NAME_BEFORE.matcher(before);
            if (name.find()) {
                int start = from + name.start(1);
                remember(found, name.group(1), role, start, roles.end());
                continue;
            }
            int to = Math.min(text.length(), roles.end() + LOOK_AHEAD);
            Matcher after = NAME_AFTER.matcher(text.substring(roles.end(), to));
            if (after.find()) {
                int start = roles.end() + after.start(1);
                remember(found, after.group(1), role, start, start + after.group(1).length());
            }
        }
        return List.copyOf(found.values());
    }

    private static void remember(Map<String, Found> found, String rawName, String role, int start, int end) {
        String name = trimToAName(rawName);
        if (name == null) {
            return;
        }
        String key = name.toLowerCase(java.util.Locale.ROOT);
        Found held = found.get(key);
        if (held == null || role.length() > held.role().length()) {
            found.put(key, new Found(name, role, start, end));
        }
    }

    private static String trimToAName(String raw) {
        java.util.List<String> words = new java.util.ArrayList<>(
                java.util.Arrays.asList(raw.replaceAll("\\s+", " ").trim().split(" ")));
        while (words.size() > 2
                && (NOT_A_FIRST_NAME.contains(words.get(0).toLowerCase(java.util.Locale.ROOT))
                        || HONORIFICS.contains(words.get(0).replace(".", "").toLowerCase(java.util.Locale.ROOT)))) {
            words.remove(0);
        }
        if (words.size() < 2 || words.size() > 3) {
            return null;
        }
        for (String word : words) {
            // A person's name is letters, an apostrophe and a hyphen. Anything else
            // in it means the pattern caught a place name, a headline or a
            // fragment: "Americas US.CA,MEDIAandPR" and "Kris Licht.The" are not
            // people, and neither is a token that is all capitals.
            if (!word.matches("[\\p{L}][\\p{L}'’-]*") || word.toUpperCase(java.util.Locale.ROOT).equals(word)) {
                return null;
            }
        }
        String name = String.join(" ", words);
        return name.length() < 4 ? null : name;
    }

    /** Job titles that are written as initials, and are wrong in title case. */
    private static final java.util.Set<String> HONORIFICS =
            java.util.Set.of("prof", "dr", "mr", "mrs", "ms", "sir", "dame", "ing", "mag");

    private static final java.util.Set<String> SMALL_WORDS =
            java.util.Set.of("of", "for", "and", "the", "in", "at", "to", "on");

    private static final java.util.Set<String> ACRONYMS = java.util.Set.of(
            "ceo", "cfo", "cto", "cio", "ciso", "coo", "cdo", "svp", "evp", "vp", "md", "coo.");

    private static String tidy(String role) {
        String cleaned = role.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        if (cleaned.length() < 2 || cleaned.length() > MAX_ROLE) {
            return null;
        }
        String[] words = cleaned.split(" ");
        return java.util.stream.IntStream.range(0, words.length)
                .mapToObj(index -> title(words[index], index == 0))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /** Title case, except for the words English keeps lowercase in a job title. */
    private static String title(String word, boolean first) {
        if (ACRONYMS.contains(word)) {
            return word.toUpperCase(java.util.Locale.ROOT);
        }
        if (!first && SMALL_WORDS.contains(word)) {
            return word;
        }
        return word.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + word.substring(1);
    }

    public static List<String> roles() {
        return new ArrayList<>(List.of(ROLE_WORDS.split("\\|")));
    }
}
