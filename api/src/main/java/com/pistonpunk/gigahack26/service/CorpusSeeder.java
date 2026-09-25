package com.pistonpunk.gigahack26.service;

import tools.jackson.databind.ObjectMapper;
import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.CompanyAlias;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.RawCapture;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.domain.SourceKind;
import com.pistonpunk.gigahack26.domain.Verdict;
import com.pistonpunk.gigahack26.repository.jpa.CompanyAliasRepository;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.mongo.RawCaptureRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(10)
public class CorpusSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CorpusSeeder.class);
    private static final double ESTIMATED_SIZE_CONFIDENCE = 0.6;

    private final CompanyRepository companies;
    private final CompanyAliasRepository aliases;
    private final EvidenceRepository evidence;
    private final RawCaptureRepository captures;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final PolicyLibrary library;
    private final ScoreboardService scoreboard;
    private final ClassifierRouter router;
    private final CaptureStore captureStore;
    private final CorpusProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CorpusSeeder(CompanyRepository companies, CompanyAliasRepository aliases, EvidenceRepository evidence,
                        RawCaptureRepository captures, SignalRepository signals, PolicyQuestionRepository questions,
                        PolicyLibrary library, ScoreboardService scoreboard, ClassifierRouter router,
                        CaptureStore captureStore, CorpusProperties properties, ObjectMapper mapper, Clock clock) {
        this.companies = companies;
        this.aliases = aliases;
        this.evidence = evidence;
        this.captures = captures;
        this.signals = signals;
        this.questions = questions;
        this.library = library;
        this.scoreboard = scoreboard;
        this.router = router;
        this.captureStore = captureStore;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    record Overlay(String engine, Verdict verdict, double confidence, String quote, String rationale) {
    }

    @Override
    public void run(ApplicationArguments args) {
        library.ensureSeeded();

        if (!properties.seedOnStartup()) {
            log.info("corpus seeding is disabled");
            return;
        }
        if (companies.count() > 0 && !properties.forceReseed()) {
            log.info("corpus already present ({} companies, {} evidence); skipping seed",
                    companies.count(), evidence.count());
            return;
        }

        long started = System.nanoTime();
        CorpusFiles.RawCorpus raw = readJson(properties.rawPath(), CorpusFiles.RawCorpus.class);
        if (raw == null || raw.companies() == null || raw.evidence() == null) {
            log.warn("no corpus found at {}; the board will render empty", properties.rawPath());
            return;
        }

        Map<String, UUID> companyIds = persistCompanies(raw.companies());
        persistEvidence(raw.evidence(), companyIds);
        persistSignals();

        for (String serviceLine : scoreboard.serviceLines()) {
            scoreboard.rescore(serviceLine);
        }

        log.info("seeded {} companies and {} evidence units in {} ms",
                companies.count(), evidence.count(), (System.nanoTime() - started) / 1_000_000);
    }

    private Map<String, UUID> persistCompanies(List<CorpusFiles.RawCompany> raw) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        List<Company> rows = new ArrayList<>(raw.size());
        List<CompanyAlias> aliasRows = new ArrayList<>();

        for (CorpusFiles.RawCompany entry : raw) {
            UUID id = deterministicId("company:" + entry.key());
            ids.put(entry.key(), id);
            Integer employees = entry.employees() != null ? entry.employees() : employeesFor(entry.sizeBand());
            Double confidence = entry.sizeConfidence() != null
                    ? entry.sizeConfidence()
                    : (employees == null ? null : ESTIMATED_SIZE_CONFIDENCE);

            rows.add(new Company(
                    id, entry.key(), entry.name(), entry.domain(), upper(entry.country()), entry.hqCity(),
                    entry.industry(), entry.sizeBand(), employees, confidence, entry.caemCode(),
                    Boolean.TRUE.equals(entry.isCompetitor()), entry.coverageNotes(), clock.instant()));

            if (entry.aliases() != null) {
                for (String alias : entry.aliases()) {
                    if (alias != null && !alias.isBlank() && !alias.equalsIgnoreCase(entry.name())) {
                        aliasRows.add(new CompanyAlias(UUID.randomUUID(), id, alias.trim()));
                    }
                }
            }
        }

        companies.saveAll(rows);
        aliases.saveAll(aliasRows);
        return ids;
    }

    private void persistEvidence(List<CorpusFiles.RawEvidence> raw, Map<String, UUID> companyIds) {
        List<Evidence> rows = new ArrayList<>(raw.size());
        List<RawCapture> captureRows = new ArrayList<>(raw.size());

        for (CorpusFiles.RawEvidence entry : raw) {
            UUID companyId = companyIds.get(entry.companyKey());
            if (companyId == null || entry.text() == null || entry.text().isBlank()) {
                continue;
            }

            SourceKind kind = parseKind(entry.sourceKind());
            Instant observedAt = parseInstant(entry.observedAt(), clock.instant());
            Instant publishedAt = parseInstant(entry.publishedAt(), null);
            String captureId = deterministicId("capture:" + entry.key()).toString();

            rows.add(new Evidence(
                    deterministicId("evidence:" + entry.key()), entry.key(), companyId, entry.sourceUrl(),
                    entry.publisher() == null ? host(entry.sourceUrl()) : entry.publisher(), kind,
                    authorityFor(kind, entry.publisher()), entry.title(), publishedAt, observedAt,
                    entry.contentHash() == null ? "" : entry.contentHash(), entry.text().length(), captureId));

            captureRows.add(new RawCapture(captureId, entry.key(), entry.sourceUrl(), entry.publisher(),
                    entry.contentHash(), observedAt, entry.text(),
                    Map.of("sourceKind", kind.name(), "companyKey", entry.companyKey())));

            captureStore.put(entry.key(), entry.text());
        }

        evidence.saveAll(rows);
        captures.saveAll(captureRows);
    }

    private void persistSignals() {
        List<PolicyQuestion> active = questions.findByActiveTrue();
        List<Evidence> all = evidence.findAll();
        Map<String, Overlay> overlay = readOverlay();
        Map<UUID, String> names = new ConcurrentHashMap<>();
        companies.findAll().forEach(company -> names.put(company.getId(), company.getName()));

        log.info("classifying {} evidence units against {} active questions with {}",
                all.size(), active.size(), router.primaryEngineId());

        AtomicInteger struck = new AtomicInteger();
        AtomicInteger fromModel = new AtomicInteger();
        List<Signal> produced;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<Signal>>> futures = new ArrayList<>(all.size() * active.size());
            for (Evidence item : all) {
                String companyName = names.getOrDefault(item.getCompanyId(), "");
                String document = captureStore.text(item.getKey());
                for (PolicyQuestion question : active) {
                    futures.add(pool.submit(() ->
                            classifyOne(item, question, companyName, document, overlay, struck, fromModel)));
                }
            }

            produced = new ArrayList<>();
            for (Future<List<Signal>> future : futures) {
                try {
                    produced.addAll(future.get());
                } catch (Exception e) {
                    log.warn("classification task failed: {}", e.toString());
                }
            }
        }

        signals.saveAll(produced);
        log.info("stored {} signals: {} from the language model, {} from the local rubric engine, "
                        + "{} candidate rows struck because the quote was not found in its capture",
                produced.size(), fromModel.get(), produced.size() - fromModel.get(), struck.get());
    }

    private List<Signal> classifyOne(Evidence item, PolicyQuestion question, String companyName, String document,
                                     Map<String, Overlay> overlay, AtomicInteger struck, AtomicInteger fromModel) {
        if (document == null || document.isBlank()) {
            return List.of();
        }

        Overlay over = overlay.get(item.getKey() + "|" + question.getKey());
        if (over != null) {
            if (over.verdict() == Verdict.NONE) {
                return List.of();
            }
            Optional<SpanLocator.Span> span = SpanLocator.locate(document, over.quote());
            if (span.isEmpty()) {
                struck.incrementAndGet();
                return List.of();
            }
            fromModel.incrementAndGet();
            return List.of(signal(item, question, over.verdict(), over.confidence(),
                    span.get().start(), span.get().end(), over.rationale(), over.engine(), span.get().exact()));
        }

        List<Signal> produced = new ArrayList<>();
        for (ClassifierEngine.Finding finding :
                router.classify(companyName, question, document)) {
            int start = Math.max(0, Math.min(finding.spanStart(), document.length()));
            int end = Math.max(start, Math.min(finding.spanEnd(), document.length()));
            if (end - start < 2) {
                continue;
            }
            produced.add(signal(item, question, finding.verdict(), finding.confidence(), start, end,
                    finding.rationale(), router.localEngineId(), true));
        }
        return produced;
    }

    private Signal signal(Evidence item, PolicyQuestion question, Verdict verdict, double confidence,
                          int start, int end, String rationale, String engine, boolean verified) {
        return new Signal(UUID.randomUUID(), item.getId(), item.getCompanyId(), question.getId(), verdict,
                confidence, start, end, rationale, engine, verified, clock.instant());
    }

    private Map<String, Overlay> readOverlay() {
        Map<String, Overlay> overlay = new LinkedHashMap<>();
        CorpusFiles.SignalOverlay parsed =
                readJson(properties.signalsPath(), CorpusFiles.SignalOverlay.class);
        if (parsed == null || parsed.signals() == null) {
            return overlay;
        }
        String engine = parsed.engine() == null ? "language-model" : parsed.engine();
        for (CorpusFiles.OverlaySignal entry : parsed.signals()) {
            overlay.put(entry.evidenceKey() + "|" + entry.questionKey(), new Overlay(
                    engine,
                    Verdict.parse(entry.verdict()),
                    entry.confidence() == null ? 0.0 : entry.confidence(),
                    entry.quote(),
                    entry.rationale()));
        }
        log.info("loaded {} pre-extracted signals from {}", overlay.size(), properties.signalsPath());
        return overlay;
    }

    private <T> T readJson(String location, Class<T> type) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            ClassPathResource resource = new ClassPathResource(location);
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    return mapper.readValue(stream, type);
                }
            }
            Path path = Path.of(location);
            if (Files.exists(path)) {
                return mapper.readValue(Files.readString(path, StandardCharsets.UTF_8), type);
            }
            log.info("{} is not present", location);
        } catch (Exception e) {
            log.warn("could not read {}: {}", location, e.toString());
        }
        return null;
    }

    static UUID deterministicId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    static Integer employeesFor(String band) {
        if (band == null) {
            return null;
        }
        return switch (band.toLowerCase().replace(" ", "").replace(",", "")) {
            case "1-10", "1-9" -> 5;
            case "11-50" -> 30;
            case "51-200" -> 120;
            case "201-500" -> 350;
            case "501-1000" -> 750;
            case "1001-5000" -> 3000;
            case "5001-10000" -> 7500;
            case "10001+", "10000+" -> 20000;
            default -> null;
        };
    }

    private static SourceKind parseKind(String raw) {
        if (raw == null) {
            return SourceKind.NEWS;
        }
        return switch (raw.trim().toLowerCase()) {
            case "job_posting", "job", "jobs" -> SourceKind.JOB_POSTING;
            case "registry" -> SourceKind.REGISTRY;
            case "filing" -> SourceKind.FILING;
            default -> SourceKind.NEWS;
        };
    }

    private static double authorityFor(SourceKind kind, String publisher) {
        String host = publisher == null ? "" : publisher.toLowerCase();
        if (host.contains("greenhouse") || host.contains("lever.co") || host.contains("ashbyhq")
                || host.contains("smartrecruiters") || host.contains("workday")) {
            return 1.0;
        }
        if (host.contains("reuters") || host.contains("bloomberg") || host.contains("ft.com")
                || host.contains("wsj")) {
            return 0.95;
        }
        if (host.contains("prnewswire") || host.contains("businesswire") || host.contains("globenewswire")) {
            return 0.5;
        }
        return kind.defaultAuthority();
    }

    private static Instant parseInstant(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String host(String url) {
        if (url == null) {
            return "unknown";
        }
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
