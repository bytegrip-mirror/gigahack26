package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company")
public class Company {

    @Id
    private UUID id;

    @Column(name = "company_key", nullable = false, unique = true, length = 120)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String domain;

    @Column(length = 2)
    private String country;

    @Column(name = "region_key", length = 80)
    private String regionKey;

    @Column(name = "hq_city", length = 120)
    private String hqCity;

    @Column(length = 120)
    private String industry;

    @Column(name = "size_band", length = 40)
    private String sizeBand;

    private Integer employees;

    @Column(name = "size_confidence")
    private Double sizeConfidence;

    @Column(length = 12)
    private String caemCode;

    @Column(name = "is_competitor", nullable = false)
    private boolean competitor;

    @Column(name = "coverage_notes", length = 500)
    private String coverageNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Company() {
    }

    public Company(UUID id, String key, String name, String domain, String country, String hqCity,
                   String industry, String sizeBand, Integer employees, Double sizeConfidence,
                   String caemCode, boolean competitor, String coverageNotes, Instant createdAt) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.domain = domain;
        this.country = country;
        this.hqCity = hqCity;
        this.industry = industry;
        this.sizeBand = sizeBand;
        this.employees = employees;
        this.sizeConfidence = sizeConfidence;
        this.caemCode = caemCode;
        this.competitor = competitor;
        this.coverageNotes = coverageNotes;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public String getCountry() {
        return country;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public void placeIn(String regionKey) {
        this.regionKey = regionKey;
    }

    public String getHqCity() {
        return hqCity;
    }

    public String getIndustry() {
        return industry;
    }

    public String getSizeBand() {
        return sizeBand;
    }

    public Integer getEmployees() {
        return employees;
    }

    public Double getSizeConfidence() {
        return sizeConfidence;
    }

    public String getCaemCode() {
        return caemCode;
    }

    public boolean isCompetitor() {
        return competitor;
    }

    public void markCompetitor(boolean value) {
        this.competitor = value;
    }

    public String getCoverageNotes() {
        return coverageNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void rename(String name, String domain) {
        this.name = name;
        this.domain = domain;
    }
}
