package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BacktestService {

    /**
     * What the board would have said, and what the corpus did next.
     *
     * The awkward fact about a lead-scoring system is that its only honest
     * accuracy evidence would come from a sales team that has not used it yet.
     * This does not pretend to solve that. It uses the one thing that is
     * genuinely out of the system's hands — the documents themselves, and their
     * dates — and asks a narrower question: of the companies the board would
     * have called at a past date, how many went on to produce more of the
     * evidence the question asked about, against the same rate for companies it
     * would not have called.
     *
     * That is a test of whether the score precedes the evidence, which is not
     * the same as whether it precedes a purchase, and the panel says so.
     */
    /**
     * The rates are components rather than methods because they are computed for
     * the payload: a record's methods are not serialised, so a panel reading
     * calledRate() got nothing and drew a dash where the number should be.
     *
     * `powered` is false when too few companies reached a band for the comparison
     * to mean anything. The corpus is broad rather than deep — a few hundred
     * companies at roughly seven documents each — so a cutoff three months back
     * leaves about two documents per company and almost nobody clears a band on
     * them. The comparison then runs one company against three hundred and sixty,
     * and the rate from that sample is a fact about the crawl's depth wearing the
     * costume of an accuracy figure. A test whose sample cannot support its
     * conclusion has to say so — that was the sharpest criticism the research
     * levelled at its own rehearsal protocol, and it applies here.
     */
    public record Outcome(int asOfDaysAgo, Instant asOf, long companies, long withEvidence, long called,
                          long control, long calledConfirmed, long controlConfirmed,
                          Double calledRate, Double controlRate, Double lift, boolean powered,
                          List<Row> rows) {

        static Outcome of(int asOfDaysAgo, Instant asOf, long withEvidence, long called, long control,
                          long calledConfirmed, long controlConfirmed, List<Row> rows) {
            Double calledRate = called == 0 ? null : round((double) calledConfirmed / called);
            Double controlRate = control == 0 ? null : round((double) controlConfirmed / control);
            Double lift = calledRate == null || controlRate == null || controlRate == 0.0
                    ? null : round(calledRate / controlRate);
            return new Outcome(asOfDaysAgo, asOf, called + control, withEvidence, called, control,
                    calledConfirmed, controlConfirmed, calledRate, controlRate, lift,
                    called >= MIN_CALLS_TO_COMPARE, rows);
        }
    }

    public record Row(String company, String band, double probability, int before, int after, boolean confirmed) {
    }

    private static final int MIN_CALLS_TO_COMPARE = 10;
    private static final int MAX_ROWS = 40;

    private final CompanyRepository companies;
    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final ScoreboardService scoreboard;
    private final ScoringService scoring;
    private final ScoringConfig settings;
    private final Clock clock;

    public BacktestService(CompanyRepository companies, EvidenceRepository evidence, SignalRepository signals,
                           ScoreboardService scoreboard, ScoringService scoring, ScoringConfig settings,
                           Clock clock) {
        this.companies = companies;
        this.evidence = evidence;
        this.signals = signals;
        this.scoreboard = scoreboard;
        this.scoring = scoring;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Outcome replay(String serviceLine, int daysAgo) {
        Instant asOf = clock.instant().minus(Duration.ofDays(daysAgo));
        List<PolicyQuestion> questions = scoreboard.activeQuestions(serviceLine);
        if (questions.isEmpty()) {
            return Outcome.of(daysAgo, asOf, 0, 0, 0, 0, 0, List.of());
        }
        Set<UUID> questionIds = questions.stream().map(PolicyQuestion::getId).collect(Collectors.toSet());

        Map<UUID, List<Evidence>> evidenceByCompany = evidence.findAll().stream()
                .collect(Collectors.groupingBy(Evidence::getCompanyId));
        Map<UUID, List<Signal>> signalsByCompany = signals.findAll().stream()
                .filter(signal -> questionIds.contains(signal.getQuestionId()))
                .collect(Collectors.groupingBy(Signal::getCompanyId));

        ScoringProperties profile = settings.effective();
        List<Row> rows = new ArrayList<>();
        long hadEvidence = 0;
        long called = 0;
        long control = 0;
        long calledConfirmed = 0;
        long controlConfirmed = 0;

        for (Company company : companies.findAll()) {
            List<Evidence> all = evidenceByCompany.getOrDefault(company.getId(), List.of());
            if (all.isEmpty()) {
                continue;
            }
            List<Evidence> before = all.stream()
                    .filter(item -> !item.decayAnchor().isAfter(asOf))
                    .toList();
            if (before.isEmpty()) {
                continue;
            }
            hadEvidence++;
            Set<UUID> beforeIds = before.stream().map(Evidence::getId).collect(Collectors.toSet());
            List<Signal> beforeSignals = signalsByCompany.getOrDefault(company.getId(), List.of()).stream()
                    .filter(signal -> beforeIds.contains(signal.getEvidenceId()))
                    .toList();

            ScoreComputation computed = scoring.compute(company, before, beforeSignals, questions, asOf);
            boolean wouldCall = computed.band() == com.pistonpunk.gigahack26.domain.Band.PRIORITY
                    || computed.band() == com.pistonpunk.gigahack26.domain.Band.NURTURE;
            if (computed.gateReason() != null) {
                continue;
            }

            int newlyRelevant = (int) signalsByCompany.getOrDefault(company.getId(), List.of()).stream()
                    .filter(signal -> !beforeIds.contains(signal.getEvidenceId()))
                    .filter(signal -> signal.getVerdict() == com.pistonpunk.gigahack26.domain.Verdict.BUY)
                    .count();
            boolean confirmed = newlyRelevant > 0;

            if (wouldCall) {
                called++;
                if (confirmed) {
                    calledConfirmed++;
                }
            } else {
                control++;
                if (confirmed) {
                    controlConfirmed++;
                }
            }
            rows.add(new Row(company.getName(), computed.band().name(), round(computed.probability()),
                    before.size(), newlyRelevant, confirmed));
        }

        rows.sort(Comparator.comparingDouble(Row::probability).reversed());
        if (rows.size() > MAX_ROWS) {
            rows = rows.subList(0, MAX_ROWS);
        }
        return Outcome.of(daysAgo, asOf, hadEvidence, called, control, calledConfirmed,
                controlConfirmed, List.copyOf(rows));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
