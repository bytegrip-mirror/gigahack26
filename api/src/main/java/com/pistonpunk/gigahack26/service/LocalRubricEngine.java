package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Verdict;
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
    private static final double MIDPOINT = 1.6;
    private static final int MAX_FINDINGS = 3;
    private static final int MAX_DERIVED_TERMS = 14;
    private static final int PREPARED_CACHE_SIZE = 512;

    private static final Set<String> STOPWORDS = Set.of(
            "about", "after", "again", "against", "among", "because", "been", "before", "being", "between",
            "company", "could", "doing", "during", "every", "their", "there", "these", "those", "through",
            "under", "where", "which", "while", "without", "would", "other", "shall", "should", "state",
            "states", "stated", "directly", "imply", "implies", "does", "role", "roles", "rather", "than",
            "that", "this", "from", "have", "having", "into", "more", "most", "much", "only", "over", "same",
            "some", "such", "then", "they", "when", "will", "your", "yours", "announced", "announce",
            "question", "counts", "evidence", "document", "company's", "within", "whether");

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

    public static List<String> deriveCues(String text, String instructions) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : NormalizedText.of(text + " " + instructions).text().split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 6 && !STOPWORDS.contains(token)) {
                terms.add(token);
                if (terms.size() >= MAX_DERIVED_TERMS) {
                    break;
                }
            }
        }
        return List.copyOf(terms);
    }

    @Override
    public List<Finding> classify(ClassificationRequest request) {
        String document = request.document();
        PolicyQuestion question = request.question();
        if (document == null || document.isBlank()) {
            return List.of();
        }

        List<CuePattern> cues = compile(question.getCues().isEmpty()
                ? deriveCues(question.getText(), question.getInstructions())
                : question.getCues(), 1.0);
        List<CuePattern> counters = compile(question.getCounterCues(), COUNTER_MULTIPLIER);
        Set<String> questionTerms = significantTerms(question.getText() + " " + question.getInstructions());

        Prepared ready = prepare(document);
        boolean anyCue = cues.stream().anyMatch(cue -> ready.normalized().contains(cue.label()))
                || counters.stream().anyMatch(cue -> ready.normalized().contains(cue.label()));
        if (!anyCue) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
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
                if (normalized.contains(term)) {
                    overlap = Math.min(QUESTION_TERM_CAP, overlap + QUESTION_TERM_WEIGHT);
                }
            }
            score += overlap;

            double confidence = 1.0 / (1.0 + Math.exp(-SLOPE * (score - MIDPOINT)));
            if (confidence <= properties.confidenceFloor()) {
                continue;
            }

            Verdict verdict = switch (question.getPolarity()) {
                case POSITIVE -> Verdict.BUY;
                case NEGATIVE -> Verdict.COUNTER;
                case DISQUALIFYING -> Verdict.BUILD;
            };
            String rationale = hits.isEmpty()
                    ? "Rubric wording from the question matched this passage"
                    : "Cue match: " + String.join(", ", hits.stream().distinct().limit(4).toList());
            findings.add(new Finding(verdict, confidence, window.start(), window.end(), rationale));
        }

        findings.sort(Comparator.comparingDouble(Finding::confidence).reversed());
        return findings.stream().limit(MAX_FINDINGS).toList();
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
        for (String token : NormalizedText.of(text).text().split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 6 && !STOPWORDS.contains(token)) {
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
