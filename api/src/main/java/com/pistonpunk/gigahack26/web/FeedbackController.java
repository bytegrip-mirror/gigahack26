package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Feedback;
import com.pistonpunk.gigahack26.domain.RepVerdict;
import com.pistonpunk.gigahack26.repository.FeedbackRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackRepository feedback;
    private final Clock clock;

    public FeedbackController(FeedbackRepository feedback, Clock clock) {
        this.feedback = feedback;
        this.clock = clock;
    }

    public record Request(
            @NotNull UUID companyId,
            UUID questionId,
            @NotNull RepVerdict repVerdict,
            @Size(max = 500) String note) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Feedback create(@Valid @RequestBody Request request) {
        return feedback.save(new Feedback(
                UUID.randomUUID(), request.companyId(), request.questionId(), request.repVerdict(),
                request.note(), clock.instant()));
    }

    @GetMapping
    public List<Feedback> list(@RequestParam(name = "companyId", required = false) UUID companyId) {
        return companyId == null ? feedback.findAll() : feedback.findByCompanyId(companyId);
    }

    @GetMapping("/precision")
    public Map<String, Object> precision() {
        List<Feedback> all = feedback.findAll();
        long accepted = all.stream().filter(entry -> entry.getRepVerdict() == RepVerdict.ACCEPT).count();
        long rejected = all.size() - accepted;
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("decisions", all.size());
        summary.put("accepted", accepted);
        summary.put("rejected", rejected);
        summary.put("queuePrecision", all.isEmpty() ? null : round((double) accepted / all.size()));
        return summary;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
