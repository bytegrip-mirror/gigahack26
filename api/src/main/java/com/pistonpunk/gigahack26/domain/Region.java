package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "region")
public class Region {

    public enum Level {
        CONTINENT,
        REGION
    }

    @Id
    private UUID id;

    @Column(name = "region_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Level level;

    @Column(name = "parent_key", length = 80)
    private String parentKey;

    @Column(nullable = false, length = 2000)
    private String countries;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Region() {
    }

    public Region(UUID id, String key, String name, Level level, String parentKey, String countries,
                  int sortOrder, Instant createdAt) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.level = level;
        this.parentKey = parentKey;
        this.countries = countries == null ? "" : countries;
        this.sortOrder = sortOrder;
        this.active = true;
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

    public Level getLevel() {
        return level;
    }

    public String getParentKey() {
        return parentKey;
    }

    public String getCountries() {
        return countries;
    }

    public List<String> countryList() {
        return countries == null || countries.isBlank() ? List.of() : List.of(countries.split(","));
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isContinent() {
        return level == Level.CONTINENT;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void revise(String name, String countries, int sortOrder, boolean active) {
        this.name = name;
        this.countries = countries == null ? "" : countries;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
