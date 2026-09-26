package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.WeightTier;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import com.pistonpunk.gigahack26.web.dto.BoardRow;
import com.pistonpunk.gigahack26.web.dto.RescoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class RescoreService {

    private static final Logger log = LoggerFactory.getLogger(RescoreService.class);

    private final CompanyRepository companies;
    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final ScoreboardService scoreboard;
    private final BoardService board;
    private final PolicyService policies;
    private final ClassifierRouter router;
    private final ScoringProperties scoring;
    private final CaptureStore captures;
    private final Clock clock;

    public RescoreService(CompanyRepository companies, EvidenceRepository evidence, SignalRepository signals,
                          PolicyQuestionRepository questions, ScoreboardService scoreboard, BoardService board,
                          PolicyService policies, ClassifierRouter router, ScoringProperties scoring,
                          CaptureStore captures, Clock clock) {
        this.policies = policies;
        this.scoring = scoring;
        this.companies = companies;
        this.evidence = evidence;
        this.signals = signals;
        this.questions = questions;
        this.scoreboard = scoreboard;
        this.board = board;
        this.router = router;
        this.captures = captures;
        this.clock = clock;
    }

    public record Outcome(int signalsProduced, int documentsScanned, long tookMs, String engine,
                          boolean keptPrevious) {
    }

    @Transactional
    public Outcome reclassify(List<PolicyQuestion> candidates, boolean replace, String engineOverride) {
        long started = System.nanoTime();
        List<PolicyQuestion> target = ScoreboardService.distinct(candidates);
        List<Evidence> all = evidence.findAll();
        Map<UUID, String> names = companies.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));

        AtomicInteger produced = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Signal> collected;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<Signal>>> futures = new ArrayList<>(all.size() * target.size());
            for (Evidence item : all) {
                String document = captures.text(item.getKey());
                if (document == null || document.isBlank()) {
                    continue;
                }
                String companyName = names.getOrDefault(item.getCompanyId(), "");
                for (PolicyQuestion question : target) {
                    futures.add(pool.submit(() ->
                            classify(item, question, companyName, document, engineOverride)));
                }
            }
            collected = new ArrayList<>();
            for (Future<List<Signal>> future : futures) {
                try {
                    collected.addAll(future.get());
                } catch (Exception e) {
                    failures.incrementAndGet();
                    log.warn("rescore task failed: {}", e.toString());
                }
            }
        }
        produced.set(collected.size());

        boolean keptPrevious = replace && !target.isEmpty() && collected.isEmpty()
                && failures.get() > 0 && heldByTarget(target) > 0;
        if (keptPrevious) {
            log.warn("every one of {} classifications failed against {} questions; keeping the {} signals "
                            + "already stored rather than emptying the board",
                    all.size(), target.size(), heldByTarget(target));
        } else if (replace && !target.isEmpty()) {
            for (PolicyQuestion question : target) {
                signals.deleteByQuestionId(question.getId());
            }
            signals.saveAll(collected);
        } else {
            signals.saveAll(collected);
        }

        return new Outcome(produced.get(), all.size(), (System.nanoTime() - started) / 1_000_000,
                router.engineId(engineOverride), keptPrevious);
    }

    private long heldByTarget(List<PolicyQuestion> target) {
        long held = 0;
        for (PolicyQuestion question : target) {
            held += signals.countByQuestionId(question.getId());
        }
        return held;
    }

    @org.springframework.cache.annotation.CacheEvict(value = "board", allEntries = true)
    @Transactional
    public RescoreResponse rescore(String serviceLine, UUID questionId, String label, String engineOverride,
                                   WeightTier tier, Polarity polarity, String theme, String instructions) {
        long started = System.nanoTime();

        Map<UUID, Ranked> before = new LinkedHashMap<>();
        for (BoardRow row : board.board(serviceLine, "all", "score", null)) {
            before.put(row.id(), new Ranked(row.rank(), row.probability(), row.band()));
        }

        String askedKey = null;
        List<PolicyQuestion> target = new ArrayList<>(serviceLineQuestions(serviceLine, questionId));
        if (questionId == null && label != null && label.trim().length() >= 10 && !target.isEmpty()) {
            PolicyQuestion asked = policies.findOrCreate(serviceLine, label.trim(), tier, polarity, theme,
                    instructions);
            if (target.stream().noneMatch(question -> question.getId().equals(asked.getId()))) {
                target.add(asked);
            }
            label = asked.getText();
            askedKey = asked.getKey();
        }
        Outcome outcome = reclassify(target, true, engineOverride);
        scoreboard.rescore(serviceLine);

        List<BoardRow> after = board.board(serviceLine, "all", "score", null);

        double base = scoring.baseRate();
        List<RescoreResponse.Move> moves = new ArrayList<>();
        int movedRows = 0;
        for (BoardRow row : after) {
            Ranked previous = before.get(row.id());
            if (previous == null) {
                continue;
            }
            boolean scoredBefore = previous.probability() > base + 1e-9;
            boolean scoredAfter = row.probability() > base + 1e-9;
            int delta = previous.rank() - row.rank();
            if (delta != 0 && (scoredBefore || scoredAfter)) {
                movedRows++;
            }
            if (delta == 0 && Math.abs(previous.probability() - row.probability()) < 0.0005) {
                continue;
            }
            moves.add(new RescoreResponse.Move(
                    row.id(),
                    row.name(),
                    previous.rank(),
                    row.rank(),
                    delta,
                    previous.probability(),
                    row.probability(),
                    row.band(),
                    delta > 0 ? "up" : delta < 0 ? "down" : "flat"));
        }
        moves.sort(Comparator.comparingInt(move -> -Math.abs(move.delta())));
        UUID leaderBefore = before.entrySet().stream()
                .min(Comparator.comparingInt(entry -> entry.getValue().rank()))
                .map(Map.Entry::getKey).orElse(null);
        UUID leaderAfter = after.isEmpty() ? null : after.get(0).id();
        int changedTop = leaderBefore != null && leaderBefore.equals(leaderAfter) ? 0 : 1;

        return new RescoreResponse(
                serviceLine,
                (System.nanoTime() - started) / 1_000_000,
                after.size(),
                outcome.documentsScanned(),
                outcome.signalsProduced(),
                outcome.engine(),
                label,
                askedKey,
                movedRows,
                changedTop,
                moves,
                after,
                List.of(),
                outcome.keptPrevious());
    }

    private List<PolicyQuestion> serviceLineQuestions(String serviceLine, UUID questionId) {
        List<PolicyQuestion> active = ScoreboardService.distinct(
                questions.findByActiveTrueAndServiceLine(serviceLine));
        if (questionId == null) {
            return active;
        }
        return active.stream().filter(question -> question.getId().equals(questionId)).toList();
    }

    private List<Signal> classify(Evidence item, PolicyQuestion question, String companyName, String document,
                                  String engineOverride) {
        ClassifierRouter.Answer answer = router.answer(companyName, question, document, engineOverride);
        List<Signal> produced = new ArrayList<>();
        for (ClassifierEngine.Finding finding : answer.findings()) {
            int start = Math.max(0, Math.min(finding.spanStart(), document.length()));
            int end = Math.max(start, Math.min(finding.spanEnd(), document.length()));
            if (end - start < 2) {
                continue;
            }
            produced.add(new Signal(UUID.randomUUID(), item.getId(), item.getCompanyId(), question.getId(),
                    finding.verdict(), finding.confidence(), start, end, finding.rationale(),
                    answer.engineId(), answer.spanVerified(), clock.instant()));
        }
        return produced;
    }

    private record Ranked(int rank, double probability, String band) {
    }
}
