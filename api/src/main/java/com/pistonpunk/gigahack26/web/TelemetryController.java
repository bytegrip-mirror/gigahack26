package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import com.pistonpunk.gigahack26.service.ClassificationCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final ClassificationCacheService cache;
    private final Clock clock;

    public TelemetryController(SignalRepository signals, EvidenceRepository evidence,
                               CompanyRepository companies, ClassificationCacheService cache, Clock clock) {
        this.signals = signals;
        this.evidence = evidence;
        this.companies = companies;
        this.cache = cache;
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
        return out;
    }
}
