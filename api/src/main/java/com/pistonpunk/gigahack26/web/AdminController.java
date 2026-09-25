package com.pistonpunk.gigahack26.web;

import tools.jackson.databind.ObjectMapper;
import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.repository.CompanyRepository;
import com.pistonpunk.gigahack26.repository.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.service.CaptureStore;
import com.pistonpunk.gigahack26.service.ClassifierEngine;
import com.pistonpunk.gigahack26.service.CorpusProperties;
import com.pistonpunk.gigahack26.service.DeepSeekEngine;
import com.pistonpunk.gigahack26.service.LocalRubricEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final double SAMPLE_RATE = 0.08;

    private final CompanyRepository companies;
    private final EvidenceRepository evidence;
    private final PolicyQuestionRepository questions;
    private final CaptureStore captures;
    private final DeepSeekEngine deepSeek;
    private final LocalRubricEngine local;
    private final CorpusProperties corpus;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AdminController(CompanyRepository companies, EvidenceRepository evidence,
                           PolicyQuestionRepository questions, CaptureStore captures, DeepSeekEngine deepSeek,
                           LocalRubricEngine local, CorpusProperties corpus, ObjectMapper mapper, Clock clock) {
        this.companies = companies;
        this.evidence = evidence;
        this.questions = questions;
        this.captures = captures;
        this.deepSeek = deepSeek;
        this.local = local;
        this.corpus = corpus;
        this.mapper = mapper;
        this.clock = clock;
    }

    @PostMapping("/signals/build")
    public ResponseEntity<Map<String, Object>> buildSignals(
            @RequestParam(name = "sampleRate", defaultValue = "0.08") double sampleRate) {
        if (!corpus.adminEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!deepSeek.available()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "DEEPSEEK_API_KEY is not configured on this instance"));
        }

        long started = System.nanoTime();
        deepSeek.resetCounters();

        List<Evidence> allEvidence = evidence.findAll();
        List<PolicyQuestion> active = questions.findByActiveTrue();
        Map<UUID, String> names = new ConcurrentHashMap<>();
        companies.findAll().forEach(company -> names.put(company.getId(), company.getName()));

        AtomicInteger candidates = new AtomicInteger();
        AtomicInteger pairs = new AtomicInteger();
        List<Map<String, Object>> signals = new ArrayList<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
            for (Evidence item : allEvidence) {
                String document = captures.text(item.getKey());
                if (document == null || document.isBlank()) {
                    continue;
                }
                String companyName = names.getOrDefault(item.getCompanyId(), "");
                for (PolicyQuestion question : active) {
                    pairs.incrementAndGet();
                    futures.add(pool.submit(() -> {
                        boolean escalated = question.getPolarity() == Polarity.DISQUALIFYING
                                || !local.classify(new ClassifierEngine.ClassificationRequest(
                                        companyName, question, document)).isEmpty()
                                || ThreadLocalRandom.current().nextDouble() < sampleRate;
                        if (!escalated) {
                            return List.of();
                        }
                        candidates.incrementAndGet();
                        List<Map<String, Object>> emitted = new ArrayList<>();
                        for (ClassifierEngine.Finding finding : deepSeek.classify(
                                new ClassifierEngine.ClassificationRequest(companyName, question, document))) {
                            int start = Math.max(0, Math.min(finding.spanStart(), document.length()));
                            int end = Math.max(start, Math.min(finding.spanEnd(), document.length()));
                            if (end - start < 2) {
                                continue;
                            }
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("evidenceKey", item.getKey());
                            entry.put("questionKey", question.getKey());
                            entry.put("verdict", finding.verdict().name());
                            entry.put("confidence", round(finding.confidence()));
                            entry.put("quote", document.substring(start, end));
                            entry.put("rationale", finding.rationale());
                            emitted.add(entry);
                        }
                        return emitted;
                    }));
                }
            }

            for (Future<List<Map<String, Object>>> future : futures) {
                try {
                    signals.addAll(future.get());
                } catch (Exception e) {
                    log.warn("signal build task failed: {}", e.toString());
                }
            }
        }

        long strikes = deepSeek.strikeCount();
        long calls = deepSeek.callCount();
        Map<String, Object> overlay = new LinkedHashMap<>();
        overlay.put("engine", deepSeek.id());
        overlay.put("generatedAt", clock.instant().toString());
        overlay.put("pairsSeen", calls);
        overlay.put("strikeRate", calls == 0 ? 0.0 : round((double) strikes / calls));
        overlay.put("signals", signals);

        String written = null;
        try {
            Path path = Path.of(corpus.signalsPath());
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(overlay),
                    StandardCharsets.UTF_8);
            written = path.toAbsolutePath().toString();
        } catch (Exception e) {
            log.warn("could not write the overlay: {}", e.toString());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pairs", pairs.get());
        result.put("escalated", candidates.get());
        result.put("modelCalls", calls);
        result.put("struckQuotes", strikes);
        result.put("strikeRate", calls == 0 ? 0.0 : round((double) strikes / calls));
        result.put("signals", signals.size());
        result.put("writtenTo", written);
        result.put("tookMs", (System.nanoTime() - started) / 1_000_000);
        return ResponseEntity.ok(result);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
