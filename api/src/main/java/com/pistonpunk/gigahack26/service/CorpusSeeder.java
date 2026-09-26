package com.pistonpunk.gigahack26.service;

import tools.jackson.databind.ObjectMapper;
import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.CompanyAlias;
import com.pistonpunk.gigahack26.domain.CorpusState;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.RawCapture;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.domain.SourceKind;
import com.pistonpunk.gigahack26.domain.Verdict;
import com.pistonpunk.gigahack26.repository.jpa.CompanyAliasRepository;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.CorpusStateRepository;
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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(10)
public class CorpusSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CorpusSeeder.class);
    private static final double ESTIMATED_SIZE_CONFIDENCE = 0.85;

    private final CompanyRepository companies;
    private final CorpusStateRepository corpusState;
    private final CompanyAliasRepository aliases;
    private final EvidenceRepository evidence;
    private final RawCaptureRepository captures;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final PolicyLibrary library;
    private final ServiceLineService serviceLines;
    private final RegionService regions;
    private final ScoreboardService scoreboard;
    private final RescoreService rescore;
    private final ClassifierRouter router;
    private final CaptureStore captureStore;
    private final CorpusProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CorpusSeeder(CompanyRepository companies, CorpusStateRepository corpusState,
                        CompanyAliasRepository aliases, EvidenceRepository evidence,
                        RawCaptureRepository captures, SignalRepository signals, PolicyQuestionRepository questions,
                        PolicyLibrary library, ServiceLineService serviceLines, RegionService regions,
                        ScoreboardService scoreboard,
                        RescoreService rescore, ClassifierRouter router,
                        CaptureStore captureStore, CorpusProperties properties, ObjectMapper mapper, Clock clock) {
        this.companies = companies;
        this.corpusState = corpusState;
        this.aliases = aliases;
        this.evidence = evidence;
        this.captures = captures;
        this.signals = signals;
        this.questions = questions;
        this.library = library;
        this.serviceLines = serviceLines;
        this.regions = regions;
        this.scoreboard = scoreboard;
        this.rescore = rescore;
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
        boolean policiesChanged = library.ensureSeeded();
        serviceLines.ensureSeeded();
        regions.ensureSeeded();

        if (!properties.seedOnStartup()) {
            log.info("corpus seeding is disabled");
            return;
        }

        String fingerprint = fingerprintOf();
        CorpusState state = corpusState.findById(CorpusState.SINGLETON).orElse(null);
        boolean corpusChanged = fingerprint != null
                && (state == null || !fingerprint.equals(state.getFingerprint()));

        if (companies.count() > 0 && !properties.forceReseed() && !corpusChanged) {
            log.info("corpus already present ({} companies, {} evidence); skipping the crawl seed",
                    companies.count(), evidence.count());
            if (policiesChanged) {
                refreshSignals();
            }
            return;
        }

        long started = System.nanoTime();
        CorpusFiles.RawCorpus raw = readJson(properties.rawPath(), CorpusFiles.RawCorpus.class);
        if (raw == null || raw.companies() == null || raw.evidence() == null) {
            log.warn("no corpus found at {}; the board will render empty", properties.rawPath());
            return;
        }

        if (corpusChanged) {
            log.info("the corpus changed since the last seed ({}); re-reading {} companies",
                    state == null ? "never seeded" : state.getFingerprint(), raw.companies().size());
        }

        Map<String, UUID> companyIds = persistCompanies(raw.companies());
        persistEvidence(raw.evidence(), companyIds);
        persistSignals();

        for (String serviceLine : scoreboard.serviceLines()) {
            scoreboard.rescore(serviceLine);
        }

        corpusState.save(new CorpusState(CorpusState.SINGLETON, fingerprint, clock.instant(),
                raw.companies().size(), raw.evidence().size()));

        log.info("seeded {} companies and {} evidence units in {} ms",
                companies.count(), evidence.count(), (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * Identifies the shipped corpus by its content, so a deploy that changes the
     * file re-reads it without an operator remembering a flag.
     *
     * Re-seeding is safe to repeat: company, evidence and capture identifiers are
     * all derived from the corpus keys, so the rows that leads, bookmarks and
     * feedback point at keep their identity across a rebuild.
     */
    private String fingerprintOf() {
        try {
            ClassPathResource resource = new ClassPathResource(properties.rawPath());
            byte[] bytes;
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    bytes = stream.readAllBytes();
                }
            } else {
                Path path = Path.of(properties.rawPath());
                if (!Files.exists(path)) {
                    return null;
                }
                bytes = Files.readAllBytes(path);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(bytes), 0, 16);
        } catch (Exception e) {
            log.warn("could not fingerprint {}: {}", properties.rawPath(), e.toString());
            return null;
        }
    }

    private void refreshSignals() {
        long started = System.nanoTime();
        List<PolicyQuestion> active = ScoreboardService.distinct(questions.findByActiveTrue());
        log.info("the shipped policy set changed; re-classifying {} documents against {} questions",
                evidence.count(), active.size());
        signals.deleteAll();
        RescoreService.Outcome outcome = rescore.reclassify(active, false, null);
        for (String serviceLine : scoreboard.serviceLines()) {
            scoreboard.rescore(serviceLine);
        }
        log.info("refreshed {} signals in {} ms ({} documents scanned)",
                outcome.signalsProduced(), (System.nanoTime() - started) / 1_000_000,
                outcome.documentsScanned());
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
                    Boolean.TRUE.equals(entry.isCompetitor())
                            || VendorRegistry.isVendor(entry.name(), entry.industry()),
                    entry.coverageNotes(), clock.instant()));

            if (entry.aliases() != null) {
                for (String alias : entry.aliases()) {
                    if (alias != null && !alias.isBlank() && !alias.equalsIgnoreCase(entry.name())) {
                        aliasRows.add(new CompanyAlias(UUID.randomUUID(), id, alias.trim()));
                    }
                }
            }
        }

        for (Company company : rows) {
            regions.regionForCountry(company.getCountry()).ifPresent(company::placeIn);
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
        dropVanished(List.copyOf(rows));
    }

    /**
     * Evidence the corpus no longer carries stops being scored.
     *
     * Writing the new rows is additive, and for a long time that was the whole
     * of it: a document that left the corpus kept its row, kept its capture and
     * kept its signals, so the deployed board answered with the union of every
     * corpus that had ever been seeded. It was scoring pages the sweep had
     * thrown away as 404s and passages a later sweep had replaced. Signals and
     * captures are rebuilt from the corpus on each seed, so anything keyed to a
     * document the corpus dropped goes with it.
     */
    private void dropVanished(List<Evidence> kept) {
        Set<String> present = kept.stream().map(Evidence::getKey).collect(Collectors.toSet());
        List<Evidence> stale = evidence.findAll().stream()
                .filter(item -> !present.contains(item.getKey()))
                .toList();
        if (stale.isEmpty()) {
            return;
        }
        for (Evidence item : stale) {
            captures.deleteByEvidenceKey(item.getKey());
            captureStore.forget(item.getKey());
        }
        signals.deleteByEvidenceIdIn(stale.stream().map(Evidence::getId).toList());
        evidence.deleteAll(stale);
        log.info("dropped {} evidence units the corpus no longer carries", stale.size());
    }

    private void persistSignals() {
        List<PolicyQuestion> active = ScoreboardService.distinct(questions.findByActiveTrue());
        List<Evidence> all = evidence.findAll();
        signals.deleteAll();
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

        ClassifierRouter.Answer answer = router.answer(companyName, question, document, null);
        List<Signal> produced = new ArrayList<>();
        for (ClassifierEngine.Finding finding : answer.findings()) {
            int start = Math.max(0, Math.min(finding.spanStart(), document.length()));
            int end = Math.max(start, Math.min(finding.spanEnd(), document.length()));
            if (end - start < 2) {
                continue;
            }
            produced.add(signal(item, question, finding.verdict(), finding.confidence(), start, end,
                    finding.rationale(), answer.engineId(), answer.spanVerified()));
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
            return 0.9;
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
