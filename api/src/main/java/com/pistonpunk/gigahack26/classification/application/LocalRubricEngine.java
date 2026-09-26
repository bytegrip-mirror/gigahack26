package com.pistonpunk.gigahack26.classification.application;

import com.pistonpunk.gigahack26.evidence.domain.NormalizedText;
import com.pistonpunk.gigahack26.evidence.domain.TextWindows;
import com.pistonpunk.gigahack26.policy.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.policy.domain.Verdict;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LocalRubricEngine implements ClassifierEngine {

    private static final double CUE_EXPONENT = 0.85;
    private static final double COUNTER_MULTIPLIER = 1.5;
    private static final double QUESTION_TERM_WEIGHT = 0.45;
    private static final double QUESTION_TERM_CAP = 1.2;
    private static final double SLOPE = 0.6;
    private static final double MIDPOINT = 1.35;
    private static final int MAX_FINDINGS = 3;
    private static final int SPAN_BUCKET = 900;
    private static final int MAX_DERIVED_TERMS = 14;
    private static final int MAX_DERIVED_PHRASES = 8;

    private static final Set<String> GENERIC = Set.of(
            "operations", "operation", "operational", "initiative", "initiatives", "programme", "programmes",
            "program", "programs", "process", "processes", "solution", "solutions", "service", "services",
            "system", "systems", "platform", "platforms", "business", "businesses", "activities", "activity",
            "capability", "capabilities", "management", "organisation", "organization", "function", "functions",
            "area", "areas", "level", "levels", "group", "groups", "team", "teams", "department", "departments",
            "strategy", "strategic", "digital", "technology", "technologies", "support", "project", "projects",
            "approach", "customer", "customers", "market", "markets", "industry", "sector", "sectors", "product",
            "products", "offering", "offerings", "vendor", "vendors", "provider", "providers", "client", "clients",
            "internal", "external", "global", "regional", "national", "local", "recent", "current", "future",
            "public", "private", "increased", "increase", "reduce", "reduced", "improve", "improved", "improving",
            "announcement", "announcements", "statement", "statements", "report", "reports", "reported",
            "published", "according", "including", "includes", "included", "following", "significant",
            "substantial", "major", "leading", "large", "small", "new", "first", "next", "last", "year", "years",
            "month", "months", "quarter", "quarters", "half", "million", "billion", "percent", "employees",
            "employee", "people", "staff", "workforce", "headcount", "position", "positions", "candidate",
            "candidates", "looking", "seeking", "opportunity", "opportunities", "responsible", "responsibilities",
            "requirements", "required", "preferred", "qualifications", "experience", "skills", "ability",
            "companies", "banks", "firms", "employers", "agencies", "operators",
            "retailers", "insurers", "carriers", "makers", "players", "giants");
    private static final int PREPARED_CACHE_SIZE = 512;

    private static final Set<String> SUBJECT = Set.of(
            "companies", "company", "banks", "bank", "firms", "firm", "businesses", "business",
            "organisations", "organizations", "employers", "employer", "groups", "agencies", "operators",
            "retailers", "insurers", "carriers", "makers", "players", "giants", "enterprises", "corporations");

    private static final Set<String> STOPWORDS = Set.of(
            "about", "after", "again", "against", "among", "because", "been", "before", "being", "between",
            "company", "could", "doing", "during", "every", "their", "there", "these", "those", "through",
            "under", "where", "which", "while", "without", "would", "other", "shall", "should", "state",
            "states", "stated", "directly", "imply", "implies", "does", "role", "roles", "rather", "than",
            "that", "this", "from", "have", "having", "into", "more", "most", "much", "only", "over", "same",
            "some", "such", "then", "they", "when", "will", "your", "yours", "announced", "announce",
            "question", "counts", "evidence", "document", "company's", "within", "whether",
            "the", "and", "for", "with", "its", "was", "were", "are", "has", "had", "not", "but",
            "all", "any", "can", "may", "one", "two", "our", "who", "what", "how", "why", "them",
            "also", "very", "is", "of", "in", "to", "on", "at", "by", "or", "as", "an", "it", "be", "do", "we", "us", "if", "no", "so", "up", "am", "he", "me", "my", "out");


    private static final List<String> CHROME = List.of(
            "we also welcome factual corrections",
            "tip-offs",
            "will protect the identity of our sources",
            "separate emails by commas",
            "maximum limit of",
            "improving everyday life",
            "you will be challenged and supported",
            "equal opportunity employer",
            "we are an equal opportunity",
            "all qualified applicants",
            "regardless of race, colour, religion",
            "regardless of race, color, religion",
            "privacy policy",
            "this website uses cookies",
            "we use cookies",
            "subscribe to our newsletter",
            "sign up for our newsletter",
            "all rights reserved",
            "forward-looking statements",
            "actual results may differ materially",
            "about the company",
            "terms and conditions",
            "follow us on",
            "click here to",
            "read more about",
            "for more information, visit",
            "this press release",
            "about us",
            "our values",
            "our purpose is",
            "our mission is",
            "great place to work",
            "best in the industry",
            "join our team",
            "we are hiring",
            "apply now",
            "job description",
            "what we offer",
            "your profile",
            "your responsibilities",
            "your tasks",
            "who we are",
            "what you will do",
            "benefits we offer",
            "share this article",
            "related articles",
            "recommended for you",
            "advertisement",
            "sponsored content");

    private static final double CHROME_MULTIPLIER = 1.6;

    private final ClassifierProperties properties;
    private final Map<String, Prepared> prepared = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Prepared> eldest) {
                    return size() > PREPARED_CACHE_SIZE;
                }
            });

    private record PreparedWindow(int start, int end, String normalized) {
    }

    private record Prepared(String normalized, List<PreparedWindow> windows) {
    }

    public LocalRubricEngine(ClassifierProperties properties) {
        this.properties = properties;
    }

    private Prepared prepare(String document) {
        Prepared cached = prepared.get(document);
        if (cached != null) {
            return cached;
        }
        List<PreparedWindow> windows = new ArrayList<>();
        for (TextWindows.Window window : TextWindows.split(document)) {
            windows.add(new PreparedWindow(window.start(), window.end(),
                    NormalizedText.of(window.text()).text()));
        }
        Prepared built = new Prepared(NormalizedText.of(document).text(), windows);
        prepared.put(document, built);
        return built;
    }

    @Override
    public String id() {
        return "local-rubric-v1";
    }

    @Override
    public boolean available() {
        return true;
    }

    /**
     * Cues for a question nobody configured, taken from its own wording.
     *
     * The rule is that a phrase has to say something. It used to accept any two
     * adjacent words whose letters added up to eight, so "companies hiring RPA
     * developers" derived the cues "companies hiring", "companies" and "hiring" —
     * and since one two-word cue clears the confidence floor on its own, that
     * question matched 1,913 of 4,466 documents and put the whole board in
     * Priority. A phrase that opens on the subject of the sentence ("companies
     * hiring", "banks migrating", "firms with") describes the audience, not the
     * signal; the content is in what follows it.
     *
     * Single words are left alone because the floor already handles them: one
     * word carries 1.0 and the midpoint is 1.35, so a lone word never reaches the
     * confidence a signal needs.
     */
    public static List<String> deriveCues(String text, String instructions) {
        List<String> tokens = contentTokens(text + " " + instructions);
        Set<String> terms = new LinkedHashSet<>();
        for (int i = 0; i + 1 < tokens.size() && terms.size() < MAX_DERIVED_PHRASES; i++) {
            String first = tokens.get(i);
            String second = tokens.get(i + 1);
            if (first.length() + second.length() >= 8 && !SUBJECT.contains(first)
                    && (!GENERIC.contains(first) || !GENERIC.contains(second))) {
                terms.add(first + " " + second);
            }
        }
        for (String token : tokens) {
            if (terms.size() >= MAX_DERIVED_TERMS) {
                break;
            }
            if (carries(token) && !GENERIC.contains(token)) {
                terms.add(token);
            }
        }
        return List.copyOf(terms);
    }

    private static final Set<String> DOMAIN_TERMS = Set.of(
            "ai", "ml", "rpa", "api", "soc", "siem", "soar", "edr", "xdr", "ciso", "iam", "sso", "mfa", "vpn",
            "sap", "erp", "crm", "hcm", "wms", "tms", "bi", "etl", "elt", "sql", "saas", "paas", "iaas", "k8s",
            "nis2", "dora", "gdpr", "sox", "hipaa", "iso", "pci", "bcp", "dr", "it", "ot", "iot", "iiot", "plm",
            "mes", "scada", "plc", "ocr", "idp", "bpm", "bpmn", "lcnc", "kpi", "roi", "tco", "fte", "sla", "itsm",
            "cmdb", "ci", "cd", "devops", "sre", "ui", "ux", "aws", "gcp", "cdn", "ddos", "phishing", "ransomware",
            "zerotrust", "sase", "sdwan", "soc2", "nist", "cis", "pci-dss", "genai", "llm", "nlp", "rag", "mlops");

    private static boolean carries(String token) {
        return token.length() >= 6 || DOMAIN_TERMS.contains(token);
    }

    private static List<String> contentTokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : NormalizedText.of(text).text().split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 2 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Everything the engine read: what it admitted, and what the floor kept out. */
    public record Outcome(List<Finding> admitted, List<Finding> rejected) {
    }

    @Override
    public List<Finding> classify(ClassificationRequest request) {
        return classifyDetailed(request).admitted();
    }

    /**
     * What the engine read and did not admit.
     *
     * A floor that silently discards its near-misses is a floor nobody can
     * check: the difference between "the corpus says nothing about this" and
     * "the corpus says something we chose not to count" is invisible on a board
     * that only shows the survivors. Both lists come back from one pass so the
     * caller never has to hold state between two calls, which matters when the
     * caller is a virtual thread.
     */
    public Outcome classifyDetailed(ClassificationRequest request) {
        String document = request.document();
        PolicyQuestion question = request.question();
        if (document == null || document.isBlank()) {
            return new Outcome(List.of(), List.of());
        }

        List<CuePattern> cues = compile(question.getCues().isEmpty()
                ? deriveCues(question.getText(), question.getInstructions())
                : question.getCues(), 1.0);
        List<CuePattern> counters = new ArrayList<>(compile(question.getCounterCues(), COUNTER_MULTIPLIER));
        counters.addAll(compile(CHROME, CHROME_MULTIPLIER));
        Set<String> questionTerms = significantTerms(question.getText() + " " + question.getInstructions());

        Prepared ready = prepare(document);
        boolean anyCue = cues.stream().anyMatch(cue -> ready.normalized().contains(cue.label()))
                || counters.stream().anyMatch(cue -> ready.normalized().contains(cue.label()));
        if (!anyCue) {
            return new Outcome(List.of(), List.of());
        }

        List<Finding> findings = new ArrayList<>();
        Map<Integer, Finding> belowFloor = new java.util.HashMap<>();
        for (PreparedWindow window : ready.windows()) {
            String normalized = window.normalized();
            double score = 0.0;
            List<String> hits = new ArrayList<>();

            for (CuePattern cue : cues) {
                if (cue.pattern().matcher(normalized).find()) {
                    score += cue.weight();
                    hits.add(cue.label());
                }
            }
            for (CuePattern counter : counters) {
                if (counter.pattern().matcher(normalized).find()) {
                    score -= counter.weight();
                }
            }

            double overlap = 0.0;
            for (String term : questionTerms) {
                if (mentions(normalized, term)) {
                    overlap = Math.min(QUESTION_TERM_CAP, overlap + QUESTION_TERM_WEIGHT);
                }
            }
            score += overlap;

            double confidence = 1.0 / (1.0 + Math.exp(-SLOPE * (score - MIDPOINT)));
            if (confidence <= properties.confidenceFloor()) {
                belowFloor.put(window.start(), new Finding(
                        verdictFor(question), confidence, window.start(), window.end(),
                        hits.isEmpty() ? "Rubric wording matched this passage, below the floor"
                                : "Below the floor: " + String.join(", ", hits.stream().distinct().limit(3).toList())));
                continue;
            }

            Verdict verdict = verdictFor(question);
            String rationale = hits.isEmpty()
                    ? "Rubric wording from the question matched this passage"
                    : "Cue match: " + String.join(", ", hits.stream().distinct().limit(4).toList());
            findings.add(new Finding(verdict, confidence, window.start(), window.end(), rationale));
        }

        findings.sort(Comparator.comparingDouble(Finding::confidence).reversed());
        List<Finding> rejected = new ArrayList<>(belowFloor.values());
        rejected.sort(Comparator.comparingDouble(Finding::confidence).reversed());
        return new Outcome(distinctBySpan(findings), List.copyOf(rejected));
    }

    /**
     * Windows overlap, so one passage can surface as three near-identical findings.
     * Keep the strongest per span: a document that answers a question twice says so
     * with two spans, a document with one answer does not get counted three times.
     */
    private static List<Finding> distinctBySpan(List<Finding> findings) {
        Map<Integer, Finding> best = new LinkedHashMap<>();
        for (Finding finding : findings) {
            int bucket = finding.spanStart() / SPAN_BUCKET;
            Finding held = best.get(bucket);
            if (held == null || finding.confidence() > held.confidence()) {
                best.put(bucket, finding);
            }
            if (best.size() >= MAX_FINDINGS) {
                break;
            }
        }
        return List.copyOf(best.values());
    }

    private static Verdict verdictFor(PolicyQuestion question) {
        return switch (question.getPolarity()) {
            case POSITIVE -> Verdict.BUY;
            case NEGATIVE -> Verdict.COUNTER;
            case DISQUALIFYING -> Verdict.BUILD;
        };
    }

    private static boolean mentions(String haystack, String needle) {
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

    private List<CuePattern> compile(List<String> rawCues, double multiplier) {
        List<CuePattern> compiled = new ArrayList<>();
        for (String cue : rawCues) {
            String normalized = NormalizedText.of(cue).text();
            if (normalized.isBlank()) {
                continue;
            }
            int words = normalized.split(" ").length;
            double weight = multiplier * Math.pow(words, CUE_EXPONENT);
            compiled.add(new CuePattern(
                    Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalized) + "(?![\\p{L}\\p{N}])"),
                    weight,
                    normalized));
        }
        return compiled;
    }

    private Set<String> significantTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : contentTokens(text)) {
            if (carries(token) && !GENERIC.contains(token)) {
                terms.add(token);
                if (terms.size() >= MAX_DERIVED_TERMS) {
                    break;
                }
            }
        }
        return terms;
    }

    private record CuePattern(Pattern pattern, double weight, String label) {
    }
}
