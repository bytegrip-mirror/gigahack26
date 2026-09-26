package com.pistonpunk.gigahack26.board.application;

import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.region.application.RegionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AskService {

    private static final Pattern MORE_THAN = Pattern.compile(
            "(?:over|more than|above|at least|bigger than|larger than|exceeding)\\s+([\\d,]+)");
    private static final Pattern FEWER_THAN = Pattern.compile(
            "(?:fewer than|less than|under|below|smaller than|up to)\\s+([\\d,]+)");

    private static final Set<String> STOPWORDS = Set.of(
            "is", "are", "the", "a", "an", "of", "in", "at", "on", "for", "to", "and", "or", "with", "any", "all",
            "company", "companies", "firm", "firms", "business", "businesses", "that", "this", "which", "who",
            "has", "have", "does", "do", "did", "their", "its", "it", "they", "them", "from", "by", "as", "be",
            "can", "could", "would", "should", "may", "might", "will", "we", "our", "us", "you", "your", "there",
            "show", "me", "find", "list", "give", "get", "need", "want", "looking", "look", "see", "tell");

    private static final Map<String, Integer> SIZE_WORDS = sizeWords();

    private static Map<String, Integer> sizeWords() {
        Map<String, Integer> words = new LinkedHashMap<>();
        words.put("mid-market", 201);
        words.put("mid market", 201);
        words.put("midmarket", 201);
        words.put("enterprise", 5001);
        words.put("large", 1001);
        words.put("big", 1001);
        words.put("medium", 201);
        words.put("start-up", 0);
        words.put("startup", 0);
        words.put("smb", 0);
        words.put("small", 0);
        return words;
    }

    private static final Map<String, String> INDUSTRY_SYNONYMS = industrySynonyms();

    private static Map<String, String> industrySynonyms() {
        Map<String, String> words = new LinkedHashMap<>();
        words.put("banking", "Banking");
        words.put("banks", "Banking");
        words.put("bank", "Banking");
        words.put("insurers", "Insurance");
        words.put("insurer", "Insurance");
        words.put("insurance", "Insurance");
        words.put("retailers", "Retail");
        words.put("retailer", "Retail");
        words.put("retail", "Retail");
        words.put("supermarkets", "Retail");
        words.put("supermarket", "Retail");
        words.put("grocers", "Retail");
        words.put("grocer", "Retail");
        words.put("telcos", "Telecom");
        words.put("telco", "Telecom");
        words.put("telecoms", "Telecom");
        words.put("telecom", "Telecom");
        words.put("pharma", "Pharmaceuticals");
        words.put("pharmaceuticals", "Pharmaceuticals");
        words.put("pharmaceutical", "Pharmaceuticals");
        words.put("hospitals", "Healthcare");
        words.put("hospital", "Healthcare");
        words.put("healthcare", "Healthcare");
        words.put("logistics", "Logistics");
        words.put("freight", "Logistics");
        words.put("shipping", "Shipping");
        words.put("airlines", "Airlines");
        words.put("airline", "Airlines");
        words.put("utilities", "Utilities");
        words.put("utility", "Utilities");
        words.put("software", "Software");
        words.put("manufacturers", "Manufacturing");
        words.put("manufacturer", "Manufacturing");
        words.put("manufacturing", "Manufacturing");
        words.put("automotive", "Automotive");
        words.put("automakers", "Automotive");
        words.put("automaker", "Automotive");
        words.put("carmakers", "Automotive");
        words.put("carmaker", "Automotive");
        words.put("energy", "Renewable Energy");
        words.put("consultancies", "Management Consulting");
        words.put("consultancy", "Management Consulting");
        words.put("consulting", "Management Consulting");
        words.put("outsourcing", "Business Process Outsourcing");
        words.put("bpo", "Business Process Outsourcing");
        words.put("defence", "Defence");
        words.put("defense", "Defence");
        words.put("chemicals", "Chemicals");
        words.put("mining", "Mining");
        words.put("ecommerce", "E-Commerce");
        words.put("e-commerce", "E-Commerce");
        words.put("payments", "Payments");
        words.put("fintech", "Financial Technology");
        words.put("insurtech", "Insurance Software");
        return words;
    }

    private static final Map<String, String> FLAGS = flags();

    private static Map<String, String> flags() {
        Map<String, String> words = new LinkedHashMap<>();
        words.put("competitors", "true");
        words.put("competitor", "true");
        words.put("rivals", "true");
        words.put("rival", "true");
        return words;
    }

    private static final long SNAPSHOT_TTL_MS = 30_000;

    private final CompanyRepository companies;
    private final RegionService regions;
    private volatile List<Company> snapshot = List.of();
    private volatile long snapshotAt = 0;

    public AskService(CompanyRepository companies, RegionService regions) {
        this.companies = companies;
        this.regions = regions;
    }

    /**
     * The question is interpreted on every keystroke, so the company list is held
     * briefly rather than read from Postgres each time. It only changes on a
     * re-seed, which is far slower than the window.
     */
    private List<Company> corpus() {
        long now = System.currentTimeMillis();
        List<Company> held = snapshot;
        if (!held.isEmpty() && now - snapshotAt < SNAPSHOT_TTL_MS) {
            return held;
        }
        synchronized (this) {
            if (snapshot.isEmpty() || System.currentTimeMillis() - snapshotAt >= SNAPSHOT_TTL_MS) {
                snapshot = companies.findAll();
                snapshotAt = System.currentTimeMillis();
            }
            return snapshot;
        }
    }

    public record Facet(String kind, String value, String label, int matches) {
    }

    public record Interpretation(String kind, List<Facet> facets, String reading, String question,
                                 Set<UUID> matched) {
    }

    public Interpretation interpret(String question) {
        String text = " " + (question == null ? "" : question.toLowerCase(Locale.ROOT)) + " ";
        List<Company> all = corpus();
        List<Facet> facets = new ArrayList<>();
        List<String> consumed = new ArrayList<>();

        Map<String, String> regionNames = new LinkedHashMap<>();
        for (RegionService.Node continent : regions.tree()) {
            regionNames.put(continent.name().toLowerCase(Locale.ROOT), continent.key());
            for (RegionService.Node child : continent.children()) {
                regionNames.put(child.name().toLowerCase(Locale.ROOT), child.key());
            }
        }

        Set<String> countryCodes = new LinkedHashSet<>();
        List<String> regionLabels = new ArrayList<>();
        Map<String, String> geography = new LinkedHashMap<>();
        geography.putAll(GeoLexicon.shorthand());
        geography.putAll(GeoLexicon.countries());
        geography.putAll(GeoLexicon.adjectives());
        for (Map.Entry<String, String> entry : byLengthDesc(geography)) {
            if (contains(text, entry.getKey())) {
                countryCodes.add(entry.getValue());
                consumed.add(entry.getKey());
            }
        }

        Set<String> regionKeys = new LinkedHashSet<>();
        Map<String, String> regionTerms = new LinkedHashMap<>(regionNames);
        regionTerms.putAll(GeoLexicon.regionAliases());
        for (Map.Entry<String, String> entry : byLengthDesc(regionTerms)) {
            if (contains(text, entry.getKey())) {
                regionKeys.add(entry.getValue());
                consumed.add(entry.getKey());
                regionLabels.add(displayRegion(entry.getValue()));
            }
        }
        for (String key : regionKeys) {
            countryCodes.addAll(regions.countriesIn(key));
        }

        if (!countryCodes.isEmpty()) {
            int matches = (int) all.stream().filter(company -> inCountry(company, countryCodes)).count();
            List<String> names = countryCodes.stream().map(GeoLexicon::name).filter(name -> !name.isBlank()).toList();
            String label = countryCodes.size() <= 3 && !names.isEmpty()
                    ? String.join(", ", names)
                    : String.join(", ", new LinkedHashSet<>(regionLabels));
            facets.add(new Facet("country", String.join(",", countryCodes),
                    label.isBlank() ? String.join(", ", names) : label, matches));
        }

        Set<String> industries = new LinkedHashSet<>();
        for (String industry : all.stream().map(Company::getIndustry)
                .filter(name -> name != null && !name.isBlank()).distinct().toList()) {
            String lower = industry.toLowerCase(Locale.ROOT);
            if (contains(text, lower) || contains(text, singular(lower))) {
                industries.add(industry);
                consumed.add(lower);
                consumed.add(singular(lower));
            }
        }
        for (Map.Entry<String, String> entry : byLengthDesc(INDUSTRY_SYNONYMS)) {
            if (contains(text, entry.getKey())) {
                industries.add(entry.getValue());
                consumed.add(entry.getKey());
            }
        }
        if (!industries.isEmpty()) {
            long matches = all.stream().filter(company -> industries.contains(company.getIndustry())).count();
            facets.add(new Facet("industry", String.join("|", industries), String.join(", ", industries),
                    (int) matches));
        }

        Matcher more = MORE_THAN.matcher(text);
        boolean sized = false;
        if (more.find()) {
            int floor = number(more.group(1));
            facets.add(new Facet("minEmployees", String.valueOf(floor), "at least " + floor + " employees",
                    (int) all.stream().filter(company -> employees(company) >= floor).count()));
            sized = true;
        }
        Matcher fewer = FEWER_THAN.matcher(text);
        if (fewer.find()) {
            int ceiling = number(fewer.group(1));
            facets.add(new Facet("maxEmployees", String.valueOf(ceiling), "up to " + ceiling + " employees",
                    (int) all.stream().filter(company -> employees(company) <= ceiling).count()));
            sized = true;
        }
        if (!sized) {
            for (Map.Entry<String, Integer> entry : SIZE_WORDS.entrySet()) {
                if (!contains(text, entry.getKey())) {
                    continue;
                }
                String band = bandFor(entry.getValue());
                facets.add(new Facet("sizeBand", band, band + " employees",
                        (int) all.stream().filter(company -> band.equals(company.getSizeBand())).count()));
                consumed.add(entry.getKey());
                break;
            }
        }

        for (Map.Entry<String, String> entry : FLAGS.entrySet()) {
            if (contains(text, entry.getKey())) {
                facets.add(new Facet("competitor", "true", "flagged competitors",
                        (int) all.stream().filter(Company::isCompetitor).count()));
                consumed.add(entry.getKey());
                break;
            }
        }

        String remainder = remainder(text, consumed);
        boolean signal = contentWords(remainder) >= 3;
        String kind = facets.isEmpty() ? "SIGNAL" : signal ? "FILTER_AND_SIGNAL" : "FILTER";
        Set<UUID> matched = facets.isEmpty() ? Set.of()
                : new LinkedHashSet<>(all.stream().filter(company -> passes(company, facets))
                        .map(Company::getId).toList());
        return new Interpretation(kind, List.copyOf(facets), reading(kind, facets), remainder, matched);
    }

    public Set<UUID> match(String question) {
        return interpret(question).matched();
    }

    private static String reading(String kind, List<Facet> facets) {
        if (facets.isEmpty()) {
            return "No company attribute is named, so this is a signal question: every stored document is classified against it.";
        }
        String what = String.join(" · ", facets.stream().map(Facet::label).toList());
        if (kind.equals("FILTER_AND_SIGNAL")) {
            return "“" + what + "” narrows the board, and the rest of the sentence is classified against every document.";
        }
        return "“" + what + "” is a fact about the company, not a document, so the board is filtered instead of the corpus re-classified.";
    }

    private String displayRegion(String key) {
        for (RegionService.Node continent : regions.tree()) {
            if (continent.key().equals(key)) {
                return continent.name();
            }
            for (RegionService.Node child : continent.children()) {
                if (child.key().equals(key)) {
                    return child.name();
                }
            }
        }
        return key;
    }

    private static String remainder(String text, List<String> consumed) {
        String out = " " + text + " ";
        List<String> phrases = new ArrayList<>(consumed);
        phrases.sort(Comparator.comparingInt(String::length).reversed());
        for (String phrase : phrases) {
            out = out.replace(" " + phrase + " ", " ");
        }
        out = MORE_THAN.matcher(out).replaceAll(" ");
        out = FEWER_THAN.matcher(out).replaceAll(" ");
        return out.replaceAll("\\s+", " ").trim();
    }

    private static List<Map.Entry<String, String>> byLengthDesc(Map<String, String> source) {
        return source.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length())
                        .reversed())
                .toList();
    }

    private static boolean inCountry(Company company, Set<String> codes) {
        return company.getCountry() != null && codes.contains(company.getCountry().toUpperCase(Locale.ROOT));
    }

    private static int contentWords(String text) {
        int count = 0;
        for (String word : text.replaceAll("[^a-z0-9\\s-]", " ").split("\\s+")) {
            if (word.length() > 2 && !STOPWORDS.contains(word)) {
                count++;
            }
        }
        return count;
    }

    private static boolean contains(String haystack, String needle) {
        if (needle.isBlank() || haystack.isBlank()) {
            return false;
        }
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            boolean startOk = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
            int end = at + needle.length();
            boolean endOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static int number(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", ""));
        } catch (NumberFormatException cause) {
            return 0;
        }
    }

    private static int employees(Company company) {
        return company.getEmployees() != null ? company.getEmployees() : floorOf(company.getSizeBand());
    }

    private static int floorOf(String band) {
        if (band == null) {
            return 0;
        }
        return switch (band) {
            case "10001+" -> 10001;
            case "5001-10000" -> 5001;
            case "1001-5000" -> 1001;
            case "501-1000" -> 501;
            case "201-500" -> 201;
            case "51-200" -> 51;
            case "11-50" -> 11;
            case "1-10" -> 1;
            default -> 0;
        };
    }

    private static String bandFor(int floor) {
        if (floor >= 10001) {
            return "10001+";
        }
        if (floor >= 5001) {
            return "5001-10000";
        }
        if (floor >= 1001) {
            return "1001-5000";
        }
        if (floor >= 501) {
            return "501-1000";
        }
        return "201-500";
    }

    private static String singular(String value) {
        return value.endsWith("s") && value.length() > 3 ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean passes(Company company, List<Facet> facets) {
        for (Facet facet : facets) {
            switch (facet.kind()) {
                case "country" -> {
                    if (!inCountry(company, Set.of(facet.value().split(",")))) {
                        return false;
                    }
                }
                case "industry" -> {
                    if (company.getIndustry() == null
                            || !Set.of(facet.value().split("\\|")).contains(company.getIndustry())) {
                        return false;
                    }
                }
                case "minEmployees" -> {
                    if (employees(company) < number(facet.value())) {
                        return false;
                    }
                }
                case "maxEmployees" -> {
                    if (employees(company) > number(facet.value())) {
                        return false;
                    }
                }
                case "sizeBand" -> {
                    if (!facet.value().equals(company.getSizeBand())) {
                        return false;
                    }
                }
                case "competitor" -> {
                    if (!company.isCompetitor()) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }
}
