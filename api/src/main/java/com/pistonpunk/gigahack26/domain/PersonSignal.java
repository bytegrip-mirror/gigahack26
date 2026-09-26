package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * What a person has said or done that is worth a call.
 *
 * A company signal is a byte range in a stored capture, so its quote is a slice
 * of a page the crawler fetched. A person signal is not: it carries its own text
 * and is marked with the origin that put it there, so nothing reads as a quote
 * that never had a document behind it.
 *
 * `kind` and `strength` are strings rather than enums on purpose. Hibernate
 * creates a check constraint with a new column and never widens it, so a new
 * enum value crash-loops a database that already holds the old set — the same
 * fault `evidence_source_kind_check` caused.
 */
@Entity
@Table(name = "person_signal")
public class PersonSignal {

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 10)
    private String strength;

    @Column(name = "signal_text", nullable = false, length = 400)
    private String text;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(length = 200)
    private String publisher;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false, length = 60)
    private String origin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PersonSignal() {
    }

    public PersonSignal(UUID id, UUID personId, String kind, String strength, String text, String sourceUrl,
                        String publisher, Instant publishedAt, String origin, Instant createdAt) {
        this.id = id;
        this.personId = personId;
        this.kind = kind;
        this.strength = strength;
        this.text = text;
        this.sourceUrl = sourceUrl;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.origin = origin;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPersonId() {
        return personId;
    }

    public String getKind() {
        return kind;
    }

    public String getStrength() {
        return strength;
    }

    public String getText() {
        return text;
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

    public String getOrigin() {
        return origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
