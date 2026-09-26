package com.pistonpunk.gigahack26.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    private UUID id;

    @Column(name = "evidence_key", nullable = false, unique = true, length = 200)
    private String key;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(nullable = false, length = 200)
    private String publisher;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 20)
    private SourceKind sourceKind;

    @Column(name = "source_authority", nullable = false)
    private double sourceAuthority;

    @Column(length = 400)
    private String title;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "content_hash", nullable = false, length = 80)
    private String contentHash;

    @Column(name = "char_length", nullable = false)
    private int charLength;

    @Column(name = "capture_id", length = 80)
    private String captureId;

    protected Evidence() {
    }

    public Evidence(UUID id, String key, UUID companyId, String sourceUrl, String publisher,
                    SourceKind sourceKind, double sourceAuthority, String title, Instant publishedAt,
                    Instant observedAt, String contentHash, int charLength, String captureId) {
        this.id = id;
        this.key = key;
        this.companyId = companyId;
        this.sourceUrl = sourceUrl;
        this.publisher = publisher;
        this.sourceKind = sourceKind;
        this.sourceAuthority = sourceAuthority;
        this.title = title;
        this.publishedAt = publishedAt;
        this.observedAt = observedAt;
        this.contentHash = contentHash;
        this.charLength = charLength;
        this.captureId = captureId;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPublisher() {
        return publisher;
    }

    public SourceKind getSourceKind() {
        return sourceKind;
    }

    public double getSourceAuthority() {
        return sourceAuthority;
    }

    public String getTitle() {
        return title;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getCharLength() {
        return charLength;
    }

    public String getCaptureId() {
        return captureId;
    }

    public Instant decayAnchor() {
        return publishedAt == null ? observedAt : publishedAt;
    }

    public String decayAnchorKind() {
        return publishedAt == null ? "observed" : "published";
    }
}
