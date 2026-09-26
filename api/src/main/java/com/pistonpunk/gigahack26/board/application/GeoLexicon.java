package com.pistonpunk.gigahack26.board.application;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GeoLexicon {

    private GeoLexicon() {
    }

    private static final Map<String, String> ADJECTIVES = Map.ofEntries(
            Map.entry("romanian", "RO"),
            Map.entry("german", "DE"),
            Map.entry("austrian", "AT"),
            Map.entry("swiss", "CH"),
            Map.entry("french", "FR"),
            Map.entry("british", "GB"),
            Map.entry("irish", "IE"),
            Map.entry("dutch", "NL"),
            Map.entry("belgian", "BE"),
            Map.entry("luxembourg", "LU"),
            Map.entry("swedish", "SE"),
            Map.entry("norwegian", "NO"),
            Map.entry("danish", "DK"),
            Map.entry("finnish", "FI"),
            Map.entry("icelandic", "IS"),
            Map.entry("spanish", "ES"),
            Map.entry("portuguese", "PT"),
            Map.entry("italian", "IT"),
            Map.entry("greek", "GR"),
            Map.entry("maltese", "MT"),
            Map.entry("cypriot", "CY"),
            Map.entry("polish", "PL"),
            Map.entry("czech", "CZ"),
            Map.entry("slovak", "SK"),
            Map.entry("slovakian", "SK"),
            Map.entry("hungarian", "HU"),
            Map.entry("slovenian", "SI"),
            Map.entry("croatian", "HR"),
            Map.entry("estonian", "EE"),
            Map.entry("latvian", "LV"),
            Map.entry("lithuanian", "LT"),
            Map.entry("bulgarian", "BG"),
            Map.entry("serbian", "RS"),
            Map.entry("bosnian", "BA"),
            Map.entry("macedonian", "MK"),
            Map.entry("albanian", "AL"),
            Map.entry("moldovan", "MD"),
            Map.entry("ukrainian", "UA"),
            Map.entry("georgian", "GE"),
            Map.entry("armenian", "AM"),
            Map.entry("azerbaijani", "AZ"),
            Map.entry("american", "US"),
            Map.entry("canadian", "CA"),
            Map.entry("mexican", "MX"),
            Map.entry("brazilian", "BR"),
            Map.entry("argentinian", "AR"),
            Map.entry("argentine", "AR"),
            Map.entry("chilean", "CL"),
            Map.entry("colombian", "CO"),
            Map.entry("peruvian", "PE"),
            Map.entry("emirati", "AE"),
            Map.entry("saudi", "SA"),
            Map.entry("qatari", "QA"),
            Map.entry("kuwaiti", "KW"),
            Map.entry("israeli", "IL"),
            Map.entry("jordanian", "JO"),
            Map.entry("lebanese", "LB"),
            Map.entry("iraqi", "IQ"),
            Map.entry("turkish", "TR"),
            Map.entry("egyptian", "EG"),
            Map.entry("moroccan", "MA"),
            Map.entry("algerian", "DZ"),
            Map.entry("tunisian", "TN"),
            Map.entry("nigerian", "NG"),
            Map.entry("ghanaian", "GH"),
            Map.entry("kenyan", "KE"),
            Map.entry("tanzanian", "TZ"),
            Map.entry("ethiopian", "ET"),
            Map.entry("rwandan", "RW"),
            Map.entry("south african", "ZA"),
            Map.entry("indian", "IN"),
            Map.entry("pakistani", "PK"),
            Map.entry("bangladeshi", "BD"),
            Map.entry("sri lankan", "LK"),
            Map.entry("singaporean", "SG"),
            Map.entry("malaysian", "MY"),
            Map.entry("thai", "TH"),
            Map.entry("vietnamese", "VN"),
            Map.entry("indonesian", "ID"),
            Map.entry("filipino", "PH"),
            Map.entry("chinese", "CN"),
            Map.entry("taiwanese", "TW"),
            Map.entry("japanese", "JP"),
            Map.entry("korean", "KR"),
            Map.entry("south korean", "KR"),
            Map.entry("australian", "AU"),
            Map.entry("new zealand", "NZ"));

    private static final Map<String, String> SHORTHAND = Map.ofEntries(
            Map.entry("uk", "GB"),
            Map.entry("u.k.", "GB"),
            Map.entry("britain", "GB"),
            Map.entry("great britain", "GB"),
            Map.entry("england", "GB"),
            Map.entry("scotland", "GB"),
            Map.entry("wales", "GB"),
            Map.entry("usa", "US"),
            Map.entry("u.s.", "US"),
            Map.entry("u.s.a.", "US"),
            Map.entry("america", "US"),
            Map.entry("united states", "US"),
            Map.entry("holland", "NL"),
            Map.entry("the netherlands", "NL"),
            Map.entry("czechia", "CZ"),
            Map.entry("czech republic", "CZ"),
            Map.entry("uae", "AE"),
            Map.entry("emirates", "AE"),
            Map.entry("dubai", "AE"),
            Map.entry("abu dhabi", "AE"),
            Map.entry("turkiye", "TR"),
            Map.entry("south korea", "KR"),
            Map.entry("korea", "KR"),
            Map.entry("hong kong", "HK"),
            Map.entry("moldavia", "MD"));

    private static final Map<String, String> REGION_ALIASES = Map.ofEntries(
            Map.entry("scandinavia", "nordics"),
            Map.entry("scandinavian", "nordics"),
            Map.entry("nordic", "nordics"),
            Map.entry("the nordics", "nordics"),
            Map.entry("benelux", "benelux"),
            Map.entry("iberia", "southern-europe"),
            Map.entry("iberian", "southern-europe"),
            Map.entry("balkans", "southeast-europe"),
            Map.entry("balkan", "southeast-europe"),
            Map.entry("central and eastern europe", "central-europe"),
            Map.entry("cee", "central-europe"),
            Map.entry("middle east", "middle-east"),
            Map.entry("gulf", "gulf"),
            Map.entry("gcc", "gulf"),
            Map.entry("north america", "north-america"),
            Map.entry("latin america", "latin-america"),
            Map.entry("latam", "latin-america"),
            Map.entry("south america", "latin-america"),
            Map.entry("asia pacific", "asia-pacific"),
            Map.entry("asia-pacific", "asia-pacific"),
            Map.entry("apac", "asia-pacific"),
            Map.entry("europe", "europe"),
            Map.entry("european", "europe"),
            Map.entry("africa", "africa"),
            Map.entry("african", "africa"));

    private static final Set<String> CODES = Set.of(
            "GB", "IE", "IM", "JE", "GG", "DE", "AT", "CH", "LI", "BE", "NL", "LU", "SE", "NO", "DK", "FI", "IS",
            "FR", "MC", "ES", "PT", "IT", "GR", "MT", "CY", "PL", "CZ", "SK", "HU", "SI", "HR", "EE", "LV", "LT",
            "RO", "BG", "RS", "BA", "MK", "AL", "ME", "MD", "XK", "UA", "BY", "GE", "AM", "AZ", "US", "CA", "BR",
            "AR", "CL", "UY", "PY", "CO", "PE", "EC", "VE", "BO", "MX", "CR", "PA", "GT", "DO", "PR", "SV", "HN",
            "NI", "AE", "SA", "QA", "KW", "BH", "OM", "IL", "JO", "LB", "IQ", "TR", "EG", "MA", "DZ", "TN", "LY",
            "NG", "GH", "CI", "SN", "CM", "KE", "TZ", "UG", "ET", "RW", "MU", "ZA", "NA", "BW", "ZW", "MZ", "AO",
            "IN", "PK", "BD", "LK", "NP", "SG", "MY", "TH", "VN", "ID", "PH", "KH", "MM", "CN", "HK", "TW", "MO",
            "JP", "KR", "AU", "NZ");

    public static String name(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return Locale.of("", code.toUpperCase(Locale.ROOT)).getDisplayCountry(Locale.ENGLISH);
    }

    public static Map<String, String> countries() {
        Map<String, String> named = new LinkedHashMap<>();
        for (String code : CODES) {
            String name = name(code);
            if (!name.isBlank()) {
                named.put(name.toLowerCase(Locale.ROOT), code);
            }
        }
        return named;
    }

    public static Map<String, String> adjectives() {
        return ADJECTIVES;
    }

    public static Map<String, String> shorthand() {
        return SHORTHAND;
    }

    public static Map<String, String> regionAliases() {
        return REGION_ALIASES;
    }
}
