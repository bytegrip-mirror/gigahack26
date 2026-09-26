package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import com.pistonpunk.gigahack26.service.ClassificationCacheService;
import com.pistonpunk.gigahack26.service.CorpusProperties;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final SignalRepository signals;
    private final EvidenceRepository evidence;
    private final CompanyRepository companies;
    private final PolicyQuestionRepository questions;
    private final ClassificationCacheService cache;
    private final CorpusProperties corpus;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TelemetryController(SignalRepository signals, EvidenceRepository evidence,
                               CompanyRepository companies, PolicyQuestionRepository questions,
                               ClassificationCacheService cache,
                               CorpusProperties corpus, ObjectMapper mapper, Clock clock) {
        this.signals = signals;
        this.evidence = evidence;
        this.companies = companies;
        this.questions = questions;
        this.cache = cache;
        this.corpus = corpus;
        this.mapper = mapper;
        this.clock = clock;
    }

    @GetMapping
    public Map<String, Object> telemetry() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checkedAt", clock.instant());

        Map<String, Long> byEngine = new LinkedHashMap<>();
        for (Object[] row : signals.countGroupedByEngine()) {
            byEngine.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        out.put("signalsByEngine", byEngine);

        long total = evidence.count();
        long documented = evidence.countDistinctCompanies();
        long companiesTotal = companies.count();
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("companies", companiesTotal);
        coverage.put("withDocuments", documented);
        coverage.put("withoutDocuments", companiesTotal - documented);
        coverage.put("documents", total);
        coverage.put("documentsPerCompany", companiesTotal == 0 ? 0.0
                : Math.round((double) total / companiesTotal * 100.0) / 100.0);
        out.put("coverage", coverage);

        Instant newest = evidence.newestObservedAt();
        Map<String, Object> freshness = new LinkedHashMap<>();
        freshness.put("newestCaptureAt", newest);
        freshness.put("newestCaptureAgeHours", newest == null ? null
                : Duration.between(newest, clock.instant()).toHours());
        out.put("freshness", freshness);

        out.put("classificationCache", cache.stats());
        out.put("signalsBySource", Map.of(
                "documents", total,
                "questions", questions.count(),
                "classificationPairs", total * questions.count()));
        out.put("overlay", overlayStats());
        return out;
    }

    /**
     * What the shipped overlay says about its own build.
     *
     * The overlay is an artefact with a provenance — which engine wrote it, how
     * many pairs it read, how many of its quotes had to be struck — and that
     * provenance is the only honest basis for a cost or accuracy claim, so it is
     * read from the artefact rather than restated in a document.
     */
    private Map<String, Object> overlayStats() {
        try {
            byte[] bytes = null;
            ClassPathResource resource = new ClassPathResource(corpus.signalsPath());
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    bytes = stream.readAllBytes();
                }
            } else {
                Path path = Path.of(corpus.signalsPath());
                if (Files.exists(path)) {
                    bytes = Files.readAllBytes(path);
                }
            }
            if (bytes == null) {
                return Map.of("present", false);
            }
            Map<String, Object> parsed = mapper.readValue(bytes, Map.class);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("present", true);
            summary.put("engine", parsed.get("engine"));
            summary.put("generatedAt", parsed.get("generatedAt"));
            summary.put("pairsSeen", parsed.get("pairsSeen"));
            summary.put("strikeRate", parsed.get("strikeRate"));
            summary.put("sampling", parsed.get("sampling"));
            return summary;
        } catch (Exception e) {
            return Map.of("present", false, "error", e.toString());
        }
    }
}
