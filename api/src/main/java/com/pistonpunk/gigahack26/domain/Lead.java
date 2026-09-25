package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead")
public class Lead {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "service_line", nullable = false, length = 80)
    private String serviceLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadStatus status;

    @Column(length = 20)
    private String channel;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(name = "contact_count", nullable = false)
    private int contactCount;

    @Column(length = 1000)
    private String note;

    @Column(name = "outreach_subject", length = 400)
    private String outreachSubject;

    @Column(name = "crm_synced_at")
    private Instant crmSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lead() {
    }

    public Lead(UUID id, UUID companyId, String serviceLine, Instant now) {
        this.id = id;
        this.companyId = companyId;
        this.serviceLine = serviceLine;
        this.status = LeadStatus.NEW;
        this.contactCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
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

    public LeadStatus getStatus() {
        return status;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getLastContactedAt() {
        return lastContactedAt;
    }

    public int getContactCount() {
        return contactCount;
    }

    public String getNote() {
        return note;
    }

    public String getOutreachSubject() {
        return outreachSubject;
    }

    public Instant getCrmSyncedAt() {
        return crmSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void record(LeadStatus status, String channel, String note, String outreachSubject, Instant now) {
        if (status != null) {
            this.status = status;
        }
        if (channel != null && !channel.isBlank()) {
            this.channel = channel.trim();
        }
        if (note != null) {
            this.note = note.isBlank() ? null : note.trim();
        }
        if (outreachSubject != null && !outreachSubject.isBlank()) {
            this.outreachSubject = outreachSubject.trim();
        }
        if (this.status != LeadStatus.NEW && this.status != LeadStatus.REJECTED) {
            this.lastContactedAt = now;
            this.contactCount++;
        }
        this.updatedAt = now;
    }

    public void markSynced(Instant now) {
        this.crmSyncedAt = now;
        this.updatedAt = now;
    }
}
