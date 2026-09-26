package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "person")
public class Person {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String role;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "span_start", nullable = false)
    private int spanStart;

    @Column(name = "span_end", nullable = false)
    private int spanEnd;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(length = 200)
    private String publisher;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(length = 160)
    private String headline;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Person() {
    }

    public Person(UUID id, UUID companyId, String name, String role, UUID evidenceId, int spanStart, int spanEnd,
                  String sourceUrl, String publisher, Instant publishedAt, Instant createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.role = role;
        this.evidenceId = evidenceId;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
        this.sourceUrl = sourceUrl;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPublisher() {
        return publisher;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getHeadline() {
        return headline;
    }

    public Instant getEnrichedAt() {
        return enrichedAt;
    }

    public void enrich(String linkedinUrl, String headline, Instant now) {
        this.linkedinUrl = linkedinUrl;
        this.headline = headline;
        this.enrichedAt = now;
    }
}
