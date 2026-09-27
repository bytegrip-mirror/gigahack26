package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A book of business, described the way a rep would describe it: where they sell,
 * to whom, how big, and who to keep out.
 *
 * The columns added after the first release are nullable and read through
 * null-safe getters. Hibernate widens a table with a new column empty, so a
 * primitive here would fail to hold the null it leaves behind — the fault that
 * once made every person unreadable.
 */
@Entity
@Table(name = "icp_profile")
public class IcpProfile {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 1000)
    private String countries;

    /** Region keys — "dach", "nordics", a whole continent. Expanded to countries
     * when the profile is matched and read alongside the ones named outright. */
    @Column(length = 1000)
    private String regions;

    @Column(nullable = false, length = 1000)
    private String industries;

    @Column(name = "min_employees", nullable = false)
    private int minEmployees;

    @Column(name = "size_bands", length = 400)
    private String sizeBands;

    /**
     * Question keys this book is looking for — ticked from the library, or
     * authored for this profile. A company is in the book when any of them has
     * fired for it.
     *
     * Nullable, and adding a column is all this needs: `ddl-auto: update` widens
     * a table, so a profile stored before this field existed reads as null and
     * means "no question filter", which is what it did.
     */
    @Column(name = "question_keys", length = 1000)
    private String questionKeys;

    @Column(name = "exclude_competitors")
    private Boolean excludeCompetitors;

    @Column(name = "untouched_only")
    private Boolean untouchedOnly;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IcpProfile() {
    }

    public IcpProfile(UUID id, String name, String countries, String regions, String industries,
                      int minEmployees, String sizeBands, String questionKeys, boolean excludeCompetitors,
                      boolean untouchedOnly, boolean active, int sortOrder, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.countries = countries;
        this.regions = regions;
        this.industries = industries;
        this.minEmployees = minEmployees;
        this.sizeBands = sizeBands;
        this.questionKeys = questionKeys;
        this.excludeCompetitors = excludeCompetitors;
        this.untouchedOnly = untouchedOnly;
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
        return countries == null ? "" : countries;
    }

    public String getRegions() {
        return regions == null ? "" : regions;
    }

    public String getIndustries() {
        return industries == null ? "" : industries;
    }

    public int getMinEmployees() {
        return minEmployees;
    }

    public String getSizeBands() {
        return sizeBands == null ? "" : sizeBands;
    }

    public boolean excludesCompetitors() {
        return excludeCompetitors != null && excludeCompetitors;
    }

    public boolean untouchedOnly() {
        return untouchedOnly != null && untouchedOnly;
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

    /**
     * The countries this profile names outright, shouted. The regions it also
     * named are expanded by the caller, which owns the taxonomy, and folded in
     * before this is asked whether a company is covered.
     */
    public Set<String> namedCountries() {
        return codes(countries);
    }

    public String getQuestionKeys() {
        return questionKeys == null ? "" : questionKeys;
    }

    /** Question keys are matched as written — they are identifiers, not words. */
    public Set<String> questions() {
        return Set.copyOf(split(getQuestionKeys(), Locale.ROOT));
    }

    /**
     * Whether a company is in this book of business.
     *
     * `geography`, `worked` and `signalled` are passed in rather than derived
     * here: the first needs the region taxonomy, the second a lead and the third
     * the signals read against this company, and none of them belongs to a
     * profile. Keeping the test in one place means the count the Studio promises
     * and the rows the board draws cannot come from two different rules.
     *
     * `signalled` is the question keys this company has a signal for. Questions
     * **filter and never score** — that is what makes a profile a description
     * rather than a weighting, and the whole reason it is safe to let a user
     * write one.
     */
    public boolean covers(Company company, Set<String> geography, boolean worked, Set<String> signalled) {
        if (!geography.isEmpty() && (company.getCountry() == null
                || !geography.contains(company.getCountry().toUpperCase(Locale.ROOT)))) {
            return false;
        }
        Set<String> wanted = words(industries);
        if (!wanted.isEmpty() && (company.getIndustry() == null
                || !wanted.contains(company.getIndustry().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        Set<String> bands = words(sizeBands);
        if (!bands.isEmpty() && (company.getSizeBand() == null
                || !bands.contains(company.getSizeBand().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (company.getEmployees() != null && company.getEmployees() < minEmployees) {
            return false;
        }
        if (excludesCompetitors() && company.isCompetitor()) {
            return false;
        }
        // Any, not all: the profile asks "is any of this happening", which is how
        // a rep reads it — not "is everything happening", which almost nobody is.
        Set<String> asked = questions();
        if (!asked.isEmpty() && Collections.disjoint(asked, signalled)) {
            return false;
        }
        return !untouchedOnly() || !worked;
    }

    private static Set<String> codes(String value) {
        return split(value, Locale.ROOT).stream().map(entry -> entry.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> words(String value) {
        return split(value, Locale.ROOT).stream().map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> split(String value, Locale locale) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    public void revise(String name, String countries, String regions, String industries,
                       int minEmployees, String sizeBands, String questionKeys, boolean excludeCompetitors,
                       boolean untouchedOnly, boolean active, int sortOrder) {
        this.name = name;
        this.countries = countries;
        this.regions = regions;
        this.industries = industries;
        this.minEmployees = minEmployees;
        this.sizeBands = sizeBands;
        this.questionKeys = questionKeys;
        this.excludeCompetitors = excludeCompetitors;
        this.untouchedOnly = untouchedOnly;
        this.active = active;
        this.sortOrder = sortOrder;
    }
}
