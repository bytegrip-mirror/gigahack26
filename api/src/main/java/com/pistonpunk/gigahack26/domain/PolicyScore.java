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
@Table(name = "policy_score")
public class PolicyScore {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "service_line", nullable = false, length = 80)
    private String serviceLine;

    @Column(nullable = false)
    private double probability;

    @Column(nullable = false)
    private double logit;

    @Column(nullable = false)
    private double evidenceNats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Band band;

    @Column(name = "gate_reason", length = 400)
    private String gateReason;

    @Column(name = "signal_count", nullable = false)
    private int signalCount;

    @Column(name = "theme_count", nullable = false)
    private int themeCount;

    @Column(name = "weakest_link", length = 300)
    private String weakestLink;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "computed_ms", nullable = false)
    private long computedMs;

    protected PolicyScore() {
    }

    public PolicyScore(UUID id, UUID companyId, String serviceLine, double probability, double logit,
                       double evidenceNats, Band band, String gateReason, int signalCount, int themeCount,
                       String weakestLink, Instant computedAt, long computedMs) {
        this.id = id;
        this.companyId = companyId;
        this.serviceLine = serviceLine;
        this.probability = probability;
        this.logit = logit;
        this.evidenceNats = evidenceNats;
        this.band = band;
        this.gateReason = gateReason;
        this.signalCount = signalCount;
        this.themeCount = themeCount;
        this.weakestLink = weakestLink;
        this.computedAt = computedAt;
        this.computedMs = computedMs;
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

    public double getProbability() {
        return probability;
    }

    public double getLogit() {
        return logit;
    }

    public double getEvidenceNats() {
        return evidenceNats;
    }

    public Band getBand() {
        return band;
    }

    public String getGateReason() {
        return gateReason;
    }

    public int getSignalCount() {
        return signalCount;
    }

    public int getThemeCount() {
        return themeCount;
    }

    public String getWeakestLink() {
        return weakestLink;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public long getComputedMs() {
        return computedMs;
    }
}
