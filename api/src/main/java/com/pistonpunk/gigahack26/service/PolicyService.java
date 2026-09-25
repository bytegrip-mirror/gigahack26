package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.DecayClass;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.domain.WeightTier;
import com.pistonpunk.gigahack26.repository.CompanyRepository;
import com.pistonpunk.gigahack26.repository.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.SignalRepository;
import com.pistonpunk.gigahack26.web.dto.PolicyRequest;
import com.pistonpunk.gigahack26.web.dto.PolicyView;
import com.pistonpunk.gigahack26.web.dto.PreviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PolicyService {

    private final PolicyQuestionRepository questions;
    private final SignalRepository signals;
    private final EvidenceRepository evidence;
    private final CompanyRepository companies;
    private final LocalRubricEngine localEngine;
    private final CaptureStore captures;
    private final Clock clock;

    public PolicyService(PolicyQuestionRepository questions, SignalRepository signals, EvidenceRepository evidence,
                         CompanyRepository companies, LocalRubricEngine localEngine, CaptureStore captures,
                         Clock clock) {
        this.questions = questions;
        this.signals = signals;
        this.evidence = evidence;
        this.companies = companies;
        this.localEngine = localEngine;
        this.captures = captures;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PolicyView> list(String serviceLine) {
        List<PolicyQuestion> found = serviceLine == null || serviceLine.isBlank()
                ? questions.findAll()
                : questions.findByServiceLineOrderByWeightTierDesc(serviceLine);

        Map<UUID, Long> counts = new ConcurrentHashMap<>();
        for (PolicyQuestion question : found) {
            counts.put(question.getId(), signals.countByQuestionId(question.getId()));
        }

        return found.stream()
                .sorted(Comparator
                        .comparing(PolicyQuestion::getServiceLine)
                        .thenComparing(question -> question.getWeightTier().ordinal())
                        .thenComparing(PolicyQuestion::getKey))
                .map(question -> toView(question, counts.getOrDefault(question.getId(), 0L)))
                .toList();
    }

    @Transactional
    public PolicyView create(PolicyRequest request) {
        PolicyQuestion question = new PolicyQuestion(
                UUID.randomUUID(),
                uniqueKey(request.serviceLine(), request.text()),
                request.serviceLine().trim(),
                request.text().trim(),
                orEmpty(request.instructions()),
                request.weightTier() == null ? WeightTier.MEDIUM : request.weightTier(),
                request.polarity() == null ? Polarity.POSITIVE : request.polarity(),
                request.theme() == null || request.theme().isBlank() ? "custom" : request.theme().trim(),
                request.decayClass() == null ? DecayClass.STRUCTURAL : request.decayClass(),
                cuesFor(request),
                request.counterCues() == null ? List.of() : clean(request.counterCues()),
                request.active() == null || request.active(),
                clock.instant());
        return toView(questions.save(question), 0L);
    }

    @Transactional
    public Optional<PolicyView> update(UUID id, PolicyRequest request) {
        return questions.findById(id).map(question -> {
            question.revise(
                    request.text().trim(),
                    orEmpty(request.instructions()),
                    request.weightTier() == null ? question.getWeightTier() : request.weightTier(),
                    request.polarity() == null ? question.getPolarity() : request.polarity(),
                    request.theme() == null || request.theme().isBlank() ? question.getTheme() : request.theme().trim(),
                    request.decayClass() == null ? question.getDecayClass() : request.decayClass(),
                    cuesFor(request),
                    request.counterCues() == null ? List.of() : clean(request.counterCues()),
                    request.active() == null || request.active(),
                    clock.instant());
            return toView(questions.save(question), signals.countByQuestionId(id));
        });
    }

    @Transactional
    public boolean delete(UUID id) {
        if (!questions.existsById(id)) {
            return false;
        }
        signals.deleteByQuestionId(id);
        questions.deleteById(id);
        return true;
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(String serviceLine, String text, String instructions, WeightTier tier,
                                   Polarity polarity, String theme) {
        long started = System.nanoTime();
        PolicyQuestion probe = new PolicyQuestion(
                UUID.randomUUID(),
                "preview",
                serviceLine,
                text == null ? "" : text,
                orEmpty(instructions),
                tier == null ? WeightTier.MEDIUM : tier,
                polarity == null ? Polarity.POSITIVE : polarity,
                theme == null || theme.isBlank() ? "custom" : theme,
                DecayClass.STRUCTURAL,
                cuesFor(new PolicyRequest(serviceLine, text == null ? "" : text, instructions, tier, polarity,
                        theme, null, null, null, null)),
                List.of(),
                true,
                clock.instant());

        List<Evidence> all = evidence.findAll();
        Map<UUID, String> names = new ConcurrentHashMap<>();
        companies.findAll().forEach(company -> names.put(company.getId(), company.getName()));

        Map<UUID, Acc> byCompany = new ConcurrentHashMap<>();
        AtomicInteger matchedEvidence = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(all.size());
            for (Evidence item : all) {
                futures.add(pool.submit(() -> {
                    String document = captures.text(item.getKey());
                    if (document == null) {
                        return;
                    }
                    List<ClassifierEngine.Finding> findings = localEngine.classify(
                            new ClassifierEngine.ClassificationRequest(
                                    names.getOrDefault(item.getCompanyId(), ""), probe, document));
                    if (findings.isEmpty()) {
                        return;
                    }
                    matchedEvidence.incrementAndGet();
                    ClassifierEngine.Finding best = findings.get(0);
                    Acc acc = byCompany.computeIfAbsent(item.getCompanyId(), key -> new Acc());
                    acc.evidence++;
                    acc.signals += findings.size();
                    if (best.confidence() > acc.topConfidence) {
                        acc.topConfidence = best.confidence();
                        acc.quote = document.substring(
                                Math.max(0, Math.min(best.spanStart(), document.length())),
                                Math.max(0, Math.min(best.spanEnd(), document.length())));
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        List<PreviewResponse.Match> matches = byCompany.entrySet().stream()
                .map(entry -> new PreviewResponse.Match(
                        entry.getKey(),
                        names.getOrDefault(entry.getKey(), ""),
                        entry.getValue().evidence,
                        entry.getValue().signals,
                        round(entry.getValue().topConfidence),
                        trim(entry.getValue().quote, 220)))
                .sorted(Comparator.comparingInt(PreviewResponse.Match::evidence).reversed())
                .limit(40)
                .toList();

        return new PreviewResponse(
                serviceLine,
                byCompany.size(),
                matchedEvidence.get(),
                all.size(),
                (System.nanoTime() - started) / 1_000_000,
                localEngine.id(),
                matches);
    }

    private PolicyView toView(PolicyQuestion question, long signalCount) {
        return new PolicyView(
                question.getId(),
                question.getKey(),
                question.getServiceLine(),
                question.getText(),
                question.getInstructions(),
                question.getWeightTier(),
                question.getWeightTier().nats(),
                question.getPolarity(),
                question.getTheme(),
                question.getDecayClass(),
                question.getDecayClass().halfLifeDays(),
                question.getCues(),
                question.getCounterCues(),
                question.isActive(),
                signalCount,
                question.getCreatedAt(),
                question.getUpdatedAt());
    }

    private List<String> cuesFor(PolicyRequest request) {
        if (request.cues() != null && !request.cues().isEmpty()) {
            return clean(request.cues());
        }
        return LocalRubricEngine.deriveCues(orEmpty(request.text()), orEmpty(request.instructions()));
    }

    private static List<String> clean(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .limit(40)
                .toList();
    }

    private String uniqueKey(String serviceLine, String text) {
        String base = slug(serviceLine) + "." + slug(firstWords(text, 3));
        String candidate = base;
        int suffix = 2;
        while (questions.findByKey(candidate).isPresent()) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static final Set<String> SLUG_STOPWORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "does", "do", "did", "has", "have", "had",
            "company", "companies", "it", "its", "this", "that", "these", "those", "of", "to", "in",
            "on", "for", "with", "and", "or", "any", "new", "own", "been", "being");

    private static String firstWords(String text, int count) {
        StringBuilder builder = new StringBuilder();
        int taken = 0;
        for (String word : orEmpty(text).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() < 3 || SLUG_STOPWORDS.contains(word)) {
                continue;
            }
            builder.append(word).append(' ');
            if (++taken == count) {
                break;
            }
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? "question" : result;
    }

    private static String slug(String value) {
        String slug = orEmpty(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max - 1) + "…";
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static final class Acc {
        private int evidence;
        private int signals;
        private double topConfidence;
        private String quote;
    }

    public Map<String, Long> signalCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Signal signal : signals.findAll()) {
            counts.merge(signal.getEngine(), 1L, Long::sum);
        }
        return counts;
    }

    public List<String> knownThemes() {
        return questions.findAll().stream().map(PolicyQuestion::getTheme).distinct().sorted().toList();
    }

    List<Company> companyList() {
        return companies.findAll();
    }
}
