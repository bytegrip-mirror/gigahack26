package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookmark",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "service_line"}))
public class Bookmark {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "service_line", nullable = false, length = 120)
    private String serviceLine;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Bookmark() {
    }

    public Bookmark(UUID id, UUID companyId, String serviceLine, String note, Instant createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.serviceLine = serviceLine;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getServiceLine() {
        return serviceLine;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void note(String note) {
        this.note = note;
    }
}
