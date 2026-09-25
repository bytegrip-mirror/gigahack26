package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Band;
import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.domain.Verdict;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScoringService {

    private static final double MIN_DECAY = 0.02;
    private static final double SIZE_CONFIDENCE_FLOOR = 0.8;
    private static final double CAP_SAFETY = 0.001;

    private final ScoringProperties properties;
    private final CaptureStore captures;

    public ScoringService(ScoringProperties properties, CaptureStore captures) {
        this.properties = properties;
        this.captures = captures;
    }

    public ScoreComputation compute(Company company, List<Evidence> evidence, List<Signal> signals,
                                    List<PolicyQuestion> questions, Instant now) {
        Map<UUID, Evidence> evidenceById = evidence.stream()
                .collect(Collectors.toMap(Evidence::getId, Function.identity(), (a, b) -> a));
        Map<UUID, PolicyQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(PolicyQuestion::getId, Function.identity(), (a, b) -> a));

        List<LedgerRow> rows = new ArrayList<>();
        boolean blockedByBuild = false;
        String buildReason = null;

        for (Signal signal : signals) {
            PolicyQuestion question = questionById.get(signal.getQuestionId());
            Evidence source = evidenceById.get(signal.getEvidenceId());
            if (question == null || source == null) {
                continue;
            }

            double ageDays = ageDays(source.decayAnchor(), now);
            double halfLife = question.getDecayClass().halfLifeDays();
            double decay = Math.max(MIN_DECAY, Math.pow(2.0, -ageDays / halfLife));
            double shrink = 2.0 * signal.getConfidence() - 1.0;
            double tierNats = question.signedMagnitude();
            double raw = tierNats * source.getSourceAuthority() * decay * shrink;
            boolean blocking = signal.getVerdict() == Verdict.BUILD || question.getPolarity() == Polarity.DISQUALIFYING;
            if (blocking) {
                blockedByBuild = true;
                buildReason = source.getPublisher() + " — " + question.getText();
            }

            rows.add(new LedgerRow(
                    source.getId(),
                    question.getId(),
                    question.getKey(),
                    question.getText(),
                    question.getTheme(),
                    question.getWeightTier(),
                    question.getPolarity(),
                    signal.getVerdict(),
                    signal.getConfidence(),
                    signal.getSpanStart(),
                    signal.getSpanEnd(),
                    span(source, signal),
                    signal.getRationale(),
                    signal.getEngine(),
                    signal.isSpanVerified(),
                    source.getSourceUrl(),
                    source.getPublisher(),
                    source.getSourceKind(),
                    source.getTitle(),
                    source.decayAnchor(),
                    source.decayAnchorKind(),
                    round(ageDays),
                    halfLife,
                    tierNats,
                    source.getSourceAuthority(),
                    round(decay),
                    round(shrink),
                    round(raw),
                    0,
                    1.0,
                    round(raw),
                    false,
                    blocking));
        }

        rows = applyRankDiscount(rows);
        rows = applyThemeCap(rows);

        double nats = rows.stream()
                .filter(row -> !row.blocking())
                .mapToDouble(LedgerRow::contributionNats)
                .sum();
        double logit = properties.baseLogit() + nats;
        double probability = sigmoid(logit);

        Optional<String> gate = gate(company, blockedByBuild, buildReason);
        Band band = band(probability, rows, gate.isPresent());

        Set<String> themes = rows.stream()
                .filter(row -> !row.blocking() && row.contributionNats() > 0)
                .map(LedgerRow::theme)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int scanned = evidence.size();
        Set<UUID> silent = evidence.stream().map(Evidence::getId).collect(Collectors.toSet());
        rows.forEach(row -> silent.remove(row.evidenceId()));

        return new ScoreComputation(
                round(probability),
                round(logit),
                round(nats),
                band,
                gate.orElse(null),
                gateLine(company, gate.orElse(null)),
                rows,
                themeTotals(rows),
                (int) rows.stream().filter(row -> !row.blocking()).count(),
                themes.size(),
                scanned,
                silent.size(),
                weakestLink(rows),
                horizon(rows, now, properties.priorityThreshold()),
                horizon(rows, now, properties.nurtureThreshold()),
                sensitivity(rows, now));
    }

    private List<LedgerRow> applyRankDiscount(List<LedgerRow> rows) {
        Map<UUID, List<LedgerRow>> byQuestion = new LinkedHashMap<>();
        List<LedgerRow> discounted = new ArrayList<>(rows.size());

        for (LedgerRow row : rows) {
            if (row.blocking()) {
                discounted.add(row);
                continue;
            }
            byQuestion.computeIfAbsent(row.questionId(), key -> new ArrayList<>()).add(row);
        }

        for (List<LedgerRow> group : byQuestion.values()) {
            group.sort(Comparator.comparingDouble((LedgerRow row) -> Math.abs(row.rawNats())).reversed());
            for (int rank = 0; rank < group.size(); rank++) {
                discounted.add(withDiscount(group.get(rank), rank,
                        Math.pow(properties.rankDiscount(), rank)));
            }
        }

        discounted.sort(Comparator.comparingDouble((LedgerRow row) -> Math.abs(row.contributionNats()))
                .reversed());
        return discounted;
    }

    private LedgerRow withDiscount(LedgerRow row, int rank, double discount) {
        return new LedgerRow(
                row.evidenceId(), row.questionId(), row.questionKey(), row.questionText(), row.theme(),
                row.tier(), row.polarity(), row.verdict(), row.confidence(), row.spanStart(), row.spanEnd(),
                row.spanText(), row.rationale(), row.engine(), row.spanVerified(), row.sourceUrl(),
                row.publisher(), row.sourceKind(), row.title(), row.anchor(), row.anchorKind(), row.ageDays(),
                row.halfLifeDays(), row.tierNats(), row.authority(), row.decay(), row.shrink(), row.rawNats(),
                rank, round(discount), round(row.rawNats() * discount), row.themeCapped(), false);
    }

    private List<LedgerRow> applyThemeCap(List<LedgerRow> rows) {
        Map<String, Double> totals = themeTotals(rows);
        double cap = properties.themeCap();
        Map<String, Double> scale = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            double total = entry.getValue();
            scale.put(entry.getKey(),
                    Math.abs(total) > cap ? (cap - CAP_SAFETY) / Math.abs(total) : 1.0);
        }

        List<LedgerRow> capped = new ArrayList<>(rows.size());
        for (LedgerRow row : rows) {
            double factor = row.blocking() ? 1.0 : scale.getOrDefault(row.theme(), 1.0);
            if (factor == 1.0) {
                capped.add(row);
                continue;
            }
            capped.add(new LedgerRow(
                    row.evidenceId(), row.questionId(), row.questionKey(), row.questionText(), row.theme(),
                    row.tier(), row.polarity(), row.verdict(), row.confidence(), row.spanStart(), row.spanEnd(),
                    row.spanText(), row.rationale(), row.engine(), row.spanVerified(), row.sourceUrl(),
                    row.publisher(), row.sourceKind(), row.title(), row.anchor(), row.anchorKind(), row.ageDays(),
                    row.halfLifeDays(), row.tierNats(), row.authority(), row.decay(), row.shrink(), row.rawNats(),
                    row.groupRank(), row.discount(), round(row.contributionNats() * factor), true, false));
        }
        return capped;
    }

    private Map<String, Double> themeTotals(List<LedgerRow> rows) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (LedgerRow row : rows) {
            if (row.blocking()) {
                continue;
            }
            totals.merge(row.theme(), row.contributionNats(), Double::sum);
        }
        totals.replaceAll((theme, total) -> round(total));
        return totals;
    }

    private Optional<String> gate(Company company, boolean blockedByBuild, String buildReason) {
        if (company.isCompetitor()) {
            return Optional.of("Competitor — provides the same services we sell");
        }
        if (blockedByBuild && buildReason != null) {
            return Optional.of("Building in-house — " + buildReason);
        }
        Integer employees = company.getEmployees();
        double confidence = company.getSizeConfidence() == null ? 0.0 : company.getSizeConfidence();
        if (employees != null && confidence >= SIZE_CONFIDENCE_FLOOR && employees < properties.icpMinEmployees()) {
            return Optional.of("Below the size floor — " + employees + " employees, ICP floor is "
                    + properties.icpMinEmployees());
        }
        if (company.getCountry() != null && !icpCountries().contains(company.getCountry())) {
            return Optional.of("Outside the ICP geography — HQ " + company.getCountry());
        }
        return Optional.empty();
    }

    private String gateLine(Company company, String gateReason) {
        String country = company.getCountry() == null ? "unknown" : company.getCountry();
        String geography = icpCountries().contains(country) ? "PASS" : "FAIL";
        String size = company.getEmployees() == null
                ? "unknown"
                : company.getEmployees() + " employees";
        String verdict = gateReason == null ? "PASS" : "FAIL";
        return "Gate: geography — HQ %s %s ∈ ICP → %s · size — %s → %s · competitor — %s"
                .formatted(company.getHqCity() == null ? "" : company.getHqCity().trim() + ", ", country,
                        geography, size, verdict, company.isCompetitor() ? "YES" : "no");
    }

    private Set<String> icpCountries() {
        Set<String> countries = new LinkedHashSet<>();
        for (String country : properties.icpCountries().split(",")) {
            String trimmed = country.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                countries.add(trimmed);
            }
        }
        return countries;
    }

    private Band band(double probability, List<LedgerRow> rows, boolean gated) {
        if (gated) {
            return Band.DISQUALIFIED;
        }
        boolean anyEvidence = rows.stream().anyMatch(row -> !row.blocking() && row.contributionNats() > 0);
        if (!anyEvidence) {
            return Band.THIN;
        }
        if (probability >= properties.priorityThreshold()) {
            return Band.PRIORITY;
        }
        if (probability >= properties.nurtureThreshold()) {
            return Band.NURTURE;
        }
        if (probability >= properties.watchThreshold()) {
            return Band.WATCH;
        }
        return Band.THIN;
    }

    private Double horizon(List<LedgerRow> rows, Instant now, double threshold) {
        if (sigmoid(properties.baseLogit() + projectedNats(rows, 0)) < threshold) {
            return null;
        }
        double low = 0;
        double high = 730;
        if (sigmoid(properties.baseLogit() + projectedNats(rows, high)) >= threshold) {
            return null;
        }
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (sigmoid(properties.baseLogit() + projectedNats(rows, mid)) >= threshold) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return round(low);
    }

    private Map<String, Double> sensitivity(List<LedgerRow> rows, Instant now) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int halfLife : new int[]{30, 90, 180}) {
            List<LedgerRow> rescaled = rows.stream().map(row -> withHalfLife(row, halfLife)).toList();
            values.put(halfLife + "d", round(sigmoid(properties.baseLogit() + projectedNats(rescaled, 0))));
        }
        return values;
    }

    private LedgerRow withHalfLife(LedgerRow row, double halfLife) {
        if (row.blocking()) {
            return row;
        }
        double decay = Math.max(MIN_DECAY, Math.pow(2.0, -row.ageDays() / halfLife));
        double raw = row.tierNats() * row.authority() * decay * row.shrink();
        return new LedgerRow(
                row.evidenceId(), row.questionId(), row.questionKey(), row.questionText(), row.theme(),
                row.tier(), row.polarity(), row.verdict(), row.confidence(), row.spanStart(), row.spanEnd(),
                row.spanText(), row.rationale(), row.engine(), row.spanVerified(), row.sourceUrl(),
                row.publisher(), row.sourceKind(), row.title(), row.anchor(), row.anchorKind(), row.ageDays(),
                halfLife, row.tierNats(), row.authority(), round(decay), row.shrink(), round(raw),
                row.groupRank(), row.discount(), round(raw * row.discount()), row.themeCapped(), false);
    }

    private double projectedNats(List<LedgerRow> rows, double projectedDays) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (LedgerRow row : rows) {
            if (row.blocking()) {
                continue;
            }
            double extra = Math.pow(2.0, -projectedDays / row.halfLifeDays());
            totals.merge(row.theme(), row.contributionNats() * extra, Double::sum);
        }
        double cap = properties.themeCap();
        double total = 0;
        for (double themeTotal : totals.values()) {
            total += Math.max(-cap, Math.min(cap, themeTotal));
        }
        return total;
    }

    private String weakestLink(List<LedgerRow> rows) {
        return rows.stream()
                .filter(row -> !row.blocking())
                .min(Comparator.comparingDouble(row -> Math.abs(row.contributionNats())))
                .map(row -> row.questionKey() + " — " + trim(row.spanText(), 140))
                .orElse(null);
    }

    private String span(Evidence evidence, Signal signal) {
        String text = captures.text(evidence.getKey());
        if (text == null) {
            return null;
        }
        int start = Math.max(0, Math.min(signal.getSpanStart(), text.length()));
        int end = Math.max(start, Math.min(signal.getSpanEnd(), text.length()));
        return text.substring(start, end);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "…";
    }

    private static double ageDays(Instant anchor, Instant now) {
        return Math.max(0, Duration.between(anchor, now).toHours() / 24.0);
    }

    private static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
