package com.pistonpunk.gigahack26.scoring.application;

import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.evidence.domain.Evidence;
import com.pistonpunk.gigahack26.evidence.domain.Signal;
import com.pistonpunk.gigahack26.evidence.infrastructure.EvidenceRepository;
import com.pistonpunk.gigahack26.evidence.infrastructure.SignalRepository;
import com.pistonpunk.gigahack26.policy.application.PolicyLibrary;
import com.pistonpunk.gigahack26.policy.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.policy.domain.ServiceLine;
import com.pistonpunk.gigahack26.policy.infrastructure.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.policy.infrastructure.ServiceLineRepository;
import com.pistonpunk.gigahack26.scoring.domain.Band;
import com.pistonpunk.gigahack26.scoring.domain.PolicyScore;
import com.pistonpunk.gigahack26.scoring.domain.ScoreComputation;
import com.pistonpunk.gigahack26.scoring.infrastructure.PolicyScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScoreboardService {

    private final CompanyRepository companies;
    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final PolicyScoreRepository scores;
    private final ServiceLineRepository lines;
    private final ScoringService scoring;
    private final Clock clock;

    public ScoreboardService(CompanyRepository companies, EvidenceRepository evidence, SignalRepository signals,
                             PolicyQuestionRepository questions, PolicyScoreRepository scores,
                             ServiceLineRepository lines,
                             ScoringService scoring, Clock clock) {
        this.companies = companies;
        this.evidence = evidence;
        this.signals = signals;
        this.questions = questions;
        this.scores = scores;
        this.lines = lines;
        this.scoring = scoring;
        this.clock = clock;
    }

    public record Computed(UUID companyId, PolicyScore score, ScoreComputation detail) {
    }

    @Transactional(readOnly = true)
    public List<Computed> compute(String serviceLine) {
        Instant now = clock.instant();
        List<PolicyQuestion> active = questions.findByActiveTrueAndServiceLine(serviceLine);
        if (active.isEmpty()) {
            return List.of();
        }

        List<Company> all = companies.findAll();
        Map<UUID, List<Evidence>> evidenceByCompany = evidence.findAll().stream()
                .collect(Collectors.groupingBy(Evidence::getCompanyId));
        Map<UUID, List<Signal>> signalsByCompany = signals.findAll().stream()
                .collect(Collectors.groupingBy(Signal::getCompanyId));

        List<Computed> computed = new ArrayList<>(all.size());
        for (Company company : all) {
            List<Evidence> companyEvidence = evidenceByCompany.getOrDefault(company.getId(), List.of());
            List<Signal> companySignals = signalsByCompany.getOrDefault(company.getId(), List.of());
            ScoreComputation detail = scoring.compute(company, companyEvidence, companySignals, active, now);
            computed.add(new Computed(company.getId(), toEntity(company, serviceLine, detail, now), detail));
        }
        return computed;
    }

    @Transactional
    public Map<String, Object> rescore(String serviceLine) {
        long started = System.nanoTime();
        List<Computed> computed = compute(serviceLine);
        scores.deleteByServiceLine(serviceLine);
        scores.saveAll(computed.stream().map(Computed::score).toList());
        long millis = (System.nanoTime() - started) / 1_000_000;
        return Map.of(
                "serviceLine", serviceLine,
                "companies", computed.size(),
                "tookMs", millis);
    }

    @Transactional
    public void rescoreAll() {
        for (String serviceLine : serviceLines()) {
            rescore(serviceLine);
        }
    }

    public List<PolicyQuestion> activeQuestions(String serviceLine) {
        return distinct(questions.findByActiveTrueAndServiceLine(serviceLine));
    }

    public static List<PolicyQuestion> distinct(List<PolicyQuestion> found) {
        Map<UUID, PolicyQuestion> byId = new LinkedHashMap<>();
        for (PolicyQuestion question : found) {
            byId.putIfAbsent(question.getId(), question);
        }
        return List.copyOf(byId.values());
    }

    public List<String> serviceLines() {
        List<String> configured = lines.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(ServiceLine::getKey)
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        List<String> fromQuestions = questions.findAll().stream()
                .map(PolicyQuestion::getServiceLine)
                .distinct()
                .sorted()
                .toList();
        return fromQuestions.isEmpty() ? PolicyLibrary.definitions().stream()
                .map(PolicyLibrary.Definition::serviceLine).distinct().sorted().toList() : fromQuestions;
    }

    public Map<String, Integer> bands(String serviceLine) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PolicyScore score : scores.findByServiceLine(serviceLine)) {
            counts.merge(score.getBand().name(), 1, Integer::sum);
        }
        return counts;
    }

    private PolicyScore toEntity(Company company, String serviceLine, ScoreComputation detail, Instant now) {
        Band band = detail.band();
        return new PolicyScore(
                UUID.randomUUID(),
                company.getId(),
                serviceLine,
                detail.probability(),
                detail.logit(),
                detail.nats(),
                band,
                detail.gateReason(),
                detail.signalCount(),
                detail.themeCount(),
                detail.weakestLink(),
                now,
                0);
    }

    public Map<UUID, ScoreComputation> details(String serviceLine) {
        Map<UUID, ScoreComputation> map = new HashMap<>();
        for (Computed computed : compute(serviceLine)) {
            map.put(computed.companyId(), computed.detail());
        }
        return map;
    }
}
