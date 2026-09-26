package com.pistonpunk.gigahack26.evidence.domain;

import com.pistonpunk.gigahack26.policy.domain.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signal")
public class Signal {

    @Id
    private UUID id;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Verdict verdict;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "span_start", nullable = false)
    private int spanStart;

    @Column(name = "span_end", nullable = false)
    private int spanEnd;

    @Column(length = 500)
    private String rationale;

    @Column(nullable = false, length = 60)
    private String engine;

    @Column(name = "span_verified", nullable = false)
    private boolean spanVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Signal() {
    }

    public Signal(UUID id, UUID evidenceId, UUID companyId, UUID questionId, Verdict verdict, double confidence,
                  int spanStart, int spanEnd, String rationale, String engine, boolean spanVerified,
                  Instant createdAt) {
        this.id = id;
        this.evidenceId = evidenceId;
        this.companyId = companyId;
        this.questionId = questionId;
        this.verdict = verdict;
        this.confidence = confidence;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
        this.rationale = rationale;
        this.engine = engine;
        this.spanVerified = spanVerified;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }

    public int spanLength() {
        return Math.max(0, spanEnd - spanStart);
    }

    public String getRationale() {
        return rationale;
    }

    public String getEngine() {
        return engine;
    }

    public boolean isSpanVerified() {
        return spanVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
