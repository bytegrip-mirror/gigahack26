package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import com.pistonpunk.gigahack26.service.CaptureStore;
import com.pistonpunk.gigahack26.service.LedgerRow;
import com.pistonpunk.gigahack26.web.dto.EvidenceDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final CompanyRepository companies;
    private final CaptureStore captures;
    private final Clock clock;

    public EvidenceController(EvidenceRepository evidence, SignalRepository signals,
                              PolicyQuestionRepository questions, CompanyRepository companies,
                              CaptureStore captures, Clock clock) {
        this.evidence = evidence;
        this.signals = signals;
        this.questions = questions;
        this.companies = companies;
        this.captures = captures;
        this.clock = clock;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvidenceDetail> detail(@PathVariable UUID id) {
        return evidence.findById(id).map(item -> {
            String text = captures.text(item.getKey());
            return ResponseEntity.ok(new EvidenceDetail(
                    item.getId(),
                    item.getKey(),
                    companies.findById(item.getCompanyId()).map(Company::getName).orElse(null),
                    item.getCompanyId(),
                    item.getTitle(),
                    item.getSourceUrl(),
                    item.getPublisher(),
                    item.getSourceKind().name(),
                    item.getSourceAuthority(),
                    item.getPublishedAt(),
                    item.getObservedAt(),
                    item.decayAnchorKind(),
                    item.getCharLength(),
                    item.getContentHash(),
                    item.getCaptureId(),
                    text,
                    rows(item, text)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private List<LedgerRow> rows(Evidence item, String text) {
        Map<UUID, PolicyQuestion> byId = questions.findAll().stream()
                .collect(Collectors.toMap(PolicyQuestion::getId, Function.identity(), (a, b) -> a));
        return signals.findByCompanyId(item.getCompanyId()).stream()
                .filter(signal -> signal.getEvidenceId().equals(item.getId()))
                .filter(signal -> byId.containsKey(signal.getQuestionId()))
                .map(signal -> {
                    PolicyQuestion question = byId.get(signal.getQuestionId());
                    double ageDays = Math.max(0, Duration.between(item.decayAnchor(), clock.instant())
                            .toHours() / 24.0);
                    return new LedgerRow(
                            item.getId(), question.getId(), question.getKey(), question.getText(),
                            question.getTheme(), question.getWeightTier(), question.getPolarity(),
                            signal.getVerdict(), signal.getConfidence(), signal.getSpanStart(), signal.getSpanEnd(),
                            slice(text, signal.getSpanStart(), signal.getSpanEnd()), signal.getRationale(),
                            signal.getEngine(), signal.isSpanVerified(), item.getSourceUrl(), item.getPublisher(),
                            item.getSourceKind(), item.getTitle(), item.decayAnchor(), item.decayAnchorKind(),
                            round(ageDays), question.getDecayClass().halfLifeDays(), question.signedMagnitude(),
                            item.getSourceAuthority(), 0, 0, 0, 0, 1.0, 0, false, false);
                })
                .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
                .toList();
    }

    private static String slice(String text, int start, int end) {
        if (text == null) {
            return null;
        }
        return text.substring(Math.max(0, Math.min(start, text.length())),
                Math.max(0, Math.min(end, text.length())));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
