package com.pistonpunk.gigahack26.classification.domain;

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
@Table(name = "classification_cache")
public class ClassificationCache {

    @Id
    private UUID id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 200)
    private String key;

    @Column(name = "content_hash", nullable = false, length = 80)
    private String contentHash;

    @Column(name = "question_key", nullable = false, length = 80)
    private String questionKey;

    @Column(nullable = false, length = 80)
    private String engine;

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

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_hit_at")
    private Instant lastHitAt;

    protected ClassificationCache() {
    }

    public ClassificationCache(UUID id, String key, String contentHash, String questionKey, String engine,
                               Verdict verdict, double confidence, int spanStart, int spanEnd, String rationale,
                               Instant now) {
        this.id = id;
        this.key = key;
        this.contentHash = contentHash;
        this.questionKey = questionKey;
        this.engine = engine;
        this.verdict = verdict;
        this.confidence = confidence;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
        this.rationale = rationale;
        this.hitCount = 0;
        this.createdAt = now;
    }

    public static String keyFor(String contentHash, String questionKey, String engine) {
        return contentHash + "|" + questionKey + "|" + engine;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getQuestionKey() {
        return questionKey;
    }

    public String getEngine() {
        return engine;
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

    public String getRationale() {
        return rationale;
    }

    public int getHitCount() {
        return hitCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastHitAt() {
        return lastHitAt;
    }

    public void hit(Instant now) {
        this.hitCount++;
        this.lastHitAt = now;
    }
}
