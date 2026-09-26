package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "icp_profile")
public class IcpProfile {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 1000)
    private String countries;

    @Column(nullable = false, length = 1000)
    private String industries;

    @Column(name = "min_employees", nullable = false)
    private int minEmployees;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IcpProfile() {
    }

    public IcpProfile(UUID id, String name, String countries, String industries, int minEmployees,
                      boolean active, int sortOrder, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.countries = countries;
        this.industries = industries;
        this.minEmployees = minEmployees;
        this.active = active;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountries() {
        return countries;
    }

    public String getIndustries() {
        return industries;
    }

    public int getMinEmployees() {
        return minEmployees;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void revise(String name, String countries, String industries, int minEmployees, boolean active,
                       int sortOrder) {
        this.name = name;
        this.countries = countries;
        this.industries = industries;
        this.minEmployees = minEmployees;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public boolean covers(Company company) {
        return coversCountry(company.getCountry()) && coversIndustry(company.getIndustry())
                && (company.getEmployees() == null || company.getEmployees() >= minEmployees);
    }

    private boolean coversCountry(String country) {
        Set<String> wanted = listed(countries);
        return wanted.isEmpty() || (country != null && wanted.contains(country.toLowerCase(Locale.ROOT)));
    }

    private boolean coversIndustry(String industry) {
        Set<String> wanted = listed(industries);
        return wanted.isEmpty() || (industry != null && wanted.contains(industry.toLowerCase(Locale.ROOT)));
    }

    private static Set<String> listed(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
