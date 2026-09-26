package com.pistonpunk.gigahack26.seeding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "corpus_state")
public class CorpusState {

    public static final UUID SINGLETON = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Id
    private UUID id;

    @Column(name = "fingerprint", length = 80)
    private String fingerprint;

    @Column(name = "seeded_at")
    private Instant seededAt;

    @Column(name = "companies")
    private int companies;

    @Column(name = "documents")
    private int documents;

    protected CorpusState() {
    }

    public CorpusState(UUID id, String fingerprint, Instant seededAt, int companies, int documents) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.seededAt = seededAt;
        this.companies = companies;
        this.documents = documents;
    }

    public UUID getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Instant getSeededAt() {
        return seededAt;
    }

    public int getCompanies() {
        return companies;
    }

    public int getDocuments() {
        return documents;
    }

    public void revise(String fingerprint, Instant seededAt, int companies, int documents) {
        this.fingerprint = fingerprint;
        this.seededAt = seededAt;
        this.companies = companies;
        this.documents = documents;
    }
}
