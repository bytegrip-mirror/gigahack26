package com.pistonpunk.gigahack26.lead.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "question_id")
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rep_verdict", nullable = false, length = 10)
    private RepVerdict repVerdict;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Feedback() {
    }

    public Feedback(UUID id, UUID companyId, UUID questionId, RepVerdict repVerdict, String note,
                    Instant createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.questionId = questionId;
        this.repVerdict = repVerdict;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public RepVerdict getRepVerdict() {
        return repVerdict;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
