package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings {

    public static final UUID SINGLETON = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "icp_countries", nullable = false, length = 1000)
    private String icpCountries;

    @Column(name = "icp_industries", nullable = false, length = 1000)
    private String icpIndustries;

    @Column(name = "icp_min_employees", nullable = false)
    private int icpMinEmployees;

    @Column(name = "geography_gate", nullable = false)
    private boolean geographyGate;

    @Column(name = "size_gate", nullable = false)
    private boolean sizeGate;

    @Column(name = "competitor_gate", nullable = false)
    private boolean competitorGate;

    @Column(name = "base_rate", nullable = false)
    private double baseRate;

    @Column(name = "priority_threshold", nullable = false)
    private double priorityThreshold;

    @Column(name = "nurture_threshold", nullable = false)
    private double nurtureThreshold;

    @Column(name = "watch_threshold", nullable = false)
    private double watchThreshold;

    @Column(name = "rank_discount", nullable = false)
    private double rankDiscount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceSettings() {
    }

    public WorkspaceSettings(UUID id, String icpCountries, String icpIndustries, int icpMinEmployees,
                             boolean geographyGate, boolean sizeGate, boolean competitorGate, double baseRate,
                             double priorityThreshold, double nurtureThreshold, double watchThreshold,
                             double rankDiscount, Instant updatedAt) {
        this.id = id;
        this.icpCountries = icpCountries;
        this.icpIndustries = icpIndustries;
        this.icpMinEmployees = icpMinEmployees;
        this.geographyGate = geographyGate;
        this.sizeGate = sizeGate;
        this.competitorGate = competitorGate;
        this.baseRate = baseRate;
        this.priorityThreshold = priorityThreshold;
        this.nurtureThreshold = nurtureThreshold;
        this.watchThreshold = watchThreshold;
        this.rankDiscount = rankDiscount;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIcpCountries() {
        return icpCountries;
    }

    public String getIcpIndustries() {
        return icpIndustries;
    }

    public int getIcpMinEmployees() {
        return icpMinEmployees;
    }

    public boolean isGeographyGate() {
        return geographyGate;
    }

    public boolean isSizeGate() {
        return sizeGate;
    }

    public boolean isCompetitorGate() {
        return competitorGate;
    }

    public double getBaseRate() {
        return baseRate;
    }

    public double getPriorityThreshold() {
        return priorityThreshold;
    }

    public double getNurtureThreshold() {
        return nurtureThreshold;
    }

    public double getWatchThreshold() {
        return watchThreshold;
    }

    public double getRankDiscount() {
        return rankDiscount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void revise(String icpCountries, String icpIndustries, int icpMinEmployees, boolean geographyGate,
                       boolean sizeGate, boolean competitorGate, double baseRate, double priorityThreshold,
                       double nurtureThreshold, double watchThreshold, double rankDiscount, Instant now) {
        this.icpCountries = icpCountries;
        this.icpIndustries = icpIndustries;
        this.icpMinEmployees = icpMinEmployees;
        this.geographyGate = geographyGate;
        this.sizeGate = sizeGate;
        this.competitorGate = competitorGate;
        this.baseRate = baseRate;
        this.priorityThreshold = priorityThreshold;
        this.nurtureThreshold = nurtureThreshold;
        this.watchThreshold = watchThreshold;
        this.rankDiscount = rankDiscount;
        this.updatedAt = now;
    }
}
