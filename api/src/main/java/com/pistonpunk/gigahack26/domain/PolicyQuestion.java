package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "policy_question")
public class PolicyQuestion {

    @Id
    private UUID id;

    @Column(name = "question_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(length = 200)
    private String title;

    @Column(name = "service_line", nullable = false, length = 80)
    private String serviceLine;

    @Column(nullable = false, length = 400)
    private String text;

    @Column(nullable = false, length = 4000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_tier", nullable = false, length = 10)
    private WeightTier weightTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Polarity polarity;

    @Column(nullable = false, length = 40)
    private String theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "decay_class", nullable = false, length = 20)
    private DecayClass decayClass;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_question_cue", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "cue", length = 200)
    private List<String> cues;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "policy_question_counter_cue", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "counter_cue", length = 200)
    private List<String> counterCues;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PolicyQuestion() {
    }

    public PolicyQuestion(UUID id, String key, String title, String serviceLine, String text, String instructions,
                          WeightTier weightTier, Polarity polarity, String theme, DecayClass decayClass,
                          List<String> cues, List<String> counterCues, boolean active, Instant createdAt) {
        this.id = id;
        this.key = key;
        this.title = title;
        this.serviceLine = serviceLine;
        this.text = text;
        this.instructions = instructions;
        this.weightTier = weightTier;
        this.polarity = polarity;
        this.theme = theme;
        this.decayClass = decayClass;
        this.cues = new ArrayList<>(cues);
        this.counterCues = new ArrayList<>(counterCues);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title == null || title.isBlank() ? text : title;
    }

    public String getServiceLine() {
        return serviceLine;
    }

    public String getText() {
        return text;
    }

    public String getInstructions() {
        return instructions;
    }

    public WeightTier getWeightTier() {
        return weightTier;
    }

    public Polarity getPolarity() {
        return polarity;
    }

    public String getTheme() {
        return theme;
    }

    public DecayClass getDecayClass() {
        return decayClass;
    }

    public List<String> getCues() {
        return cues == null ? List.of() : List.copyOf(cues);
    }

    public List<String> getCounterCues() {
        return counterCues == null ? List.of() : List.copyOf(counterCues);
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void revise(String title, String text, String instructions, WeightTier weightTier, Polarity polarity,
                       String theme, DecayClass decayClass, List<String> cues, List<String> counterCues,
                       boolean active, Instant now) {
        this.title = title;
        this.text = text;
        this.instructions = instructions;
        this.weightTier = weightTier;
        this.polarity = polarity;
        this.theme = theme;
        this.decayClass = decayClass;
        this.cues = new ArrayList<>(cues);
        this.counterCues = new ArrayList<>(counterCues);
        this.active = active;
        this.updatedAt = now;
    }

    public double signedMagnitude() {
        double magnitude = weightTier.nats();
        return polarity == Polarity.POSITIVE ? magnitude : -magnitude;
    }

    /**
     * The weight a reading carries, which is zero unless the reading asserts what
     * the question asks.
     *
     * Each engine says "the thing this question is about is present" with its own
     * verdict: the cue matcher answers BUY for a positive question, COUNTER for a
     * counter-evidence question, BUILD for a disqualifier. A verdict that asserts
     * something else is an answer, not evidence — BUILD on a positive question
     * means the company *sells* the capability, and paying that the positive
     * weight ranks vendors as buyers, which is the exact inversion this platform
     * exists to avoid.
     */
    public double magnitudeFor(Verdict verdict) {
        return asserts(verdict) ? signedMagnitude() : 0.0;
    }

    public boolean asserts(Verdict verdict) {
        return switch (polarity) {
            case POSITIVE -> verdict == Verdict.BUY;
            case NEGATIVE -> verdict == Verdict.COUNTER || verdict == Verdict.BUILD;
            case DISQUALIFYING -> verdict == Verdict.BUILD;
        };
    }
}
