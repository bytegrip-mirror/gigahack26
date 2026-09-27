package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.IcpProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcpProfileTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    /** A profile that asks nothing about questions — the shape every test but the
     * last one wants, so the question filter stays out of their way. */
    private static IcpProfile profile(String countries, String industries, int minEmployees) {
        return profile(countries, industries, minEmployees, "");
    }

    private static IcpProfile profile(String countries, String industries, int minEmployees, String questionKeys) {
        return new IcpProfile(UUID.randomUUID(), "Test", countries, "", industries, minEmployees,
                "", questionKeys, false, false, true, 0, NOW);
    }

    private static Company company(String country, String industry, Integer employees) {
        return company(country, industry, employees, "1001-5000", false);
    }

    private static Company company(String country, String industry, Integer employees, String sizeBand,
                                   boolean competitor) {
        return new Company(UUID.randomUUID(), "co", "Test Co", "test.example", country, "Berlin",
                industry, sizeBand, employees, 0.9, null, competitor, null, NOW);
    }

    /** The geography the service would hand in: the profile's own codes, plus
     * whatever its regions expand to, which the taxonomy resolves. */
    private static Set<String> here(String... countries) {
        return Set.of(countries);
    }

    @Test
    void aCountryCodeMatchesWhateverCaseItIsWrittenIn() {
        IcpProfile dach = profile("DE,AT,CH", "", 0);
        assertTrue(dach.covers(company("DE", "Automotive", 5000), dach.namedCountries(), false, Set.of()),
                "the profile stores codes folded and the company stores them shouted");
        assertFalse(dach.covers(company("FR", "Automotive", 5000), dach.namedCountries(), false, Set.of()));
    }

    @Test
    void anEmptyRuleMeansEverywhere() {
        IcpProfile anywhere = profile("", "", 0);
        assertTrue(anywhere.covers(company("FR", "Retail", null), anywhere.namedCountries(), false, Set.of()));
    }

    @Test
    void industryAndSizeHaveToHoldToo() {
        IcpProfile narrow = profile("DE", "Manufacturing,Automotive", 1000);
        assertTrue(narrow.covers(company("DE", "automotive", 5000), narrow.namedCountries(), false, Set.of()));
        assertFalse(narrow.covers(company("DE", "Retail", 5000), narrow.namedCountries(), false, Set.of()));
        assertFalse(narrow.covers(company("DE", "Automotive", 400), narrow.namedCountries(), false, Set.of()));
    }

    @Test
    void anUnknownHeadcountIsNotADisqualification() {
        IcpProfile floor = profile("DE", "", 1000);
        assertTrue(floor.covers(company("DE", "Automotive", null), floor.namedCountries(), false, Set.of()),
                "a company we have no headcount for is not a company below the floor");
    }

    @Test
    void aRegionCoversTheCountriesItExpandsToEvenThoughTheProfileNeverNamedThem() {
        IcpProfile nordics = profile("", "", 0);
        assertTrue(nordics.covers(company("SE", "Retail", 5000), here("SE", "NO", "DK", "FI", "IS"), false, Set.of()),
                "the profile picked a region; the taxonomy is what knows what that means");
        assertFalse(nordics.covers(company("DE", "Retail", 5000), here("SE", "NO", "DK", "FI", "IS"), false, Set.of()));
    }

    @Test
    void aSizeBandIsASecondWayToSayHowBig() {
        IcpProfile large = new IcpProfile(UUID.randomUUID(), "Large", "", "", "", 0,
                "10001+", "", false, false, true, 0, NOW);
        assertTrue(large.covers(company("DE", "Retail", 20000, "10001+", false), large.namedCountries(), false,
                Set.of()));
        assertFalse(large.covers(company("DE", "Retail", 3000, "1001-5000", false), large.namedCountries(), false,
                Set.of()));
    }

    @Test
    void aVendorCanBeKeptOutOfOneBookWithoutBeingKeptOutOfTheBoard() {
        IcpProfile ours = new IcpProfile(UUID.randomUUID(), "Ours", "", "", "", 0,
                "", "", true, false, true, 0, NOW);
        assertFalse(ours.covers(company("DE", "IT Services", 5000, "1001-5000", true), ours.namedCountries(), false,
                Set.of()));
        assertTrue(ours.covers(company("DE", "IT Services", 5000, "1001-5000", false), ours.namedCountries(), false,
                Set.of()));
    }

    @Test
    void untouchedOnlyLeavesOutTheOnesSomebodyHasAlreadyRung() {
        IcpProfile fresh = new IcpProfile(UUID.randomUUID(), "Fresh", "", "", "", 0,
                "", "", false, true, true, 0, NOW);
        assertTrue(fresh.covers(company("DE", "Retail", 5000), fresh.namedCountries(), false, Set.of()));
        assertFalse(fresh.covers(company("DE", "Retail", 5000), fresh.namedCountries(), true, Set.of()));
    }

    @Test
    void aProfileWithNoQuestionsAsksNothingAboutThem() {
        IcpProfile quiet = profile("DE", "", 0);
        assertTrue(quiet.covers(company("DE", "Retail", 5000), quiet.namedCountries(), false, Set.of()),
                "no questions declared is not the same as no question answered");
    }

    @Test
    void aProfileWithQuestionsWantsOneOfThemToHaveFired() {
        IcpProfile hiring = profile("", "", 0, "automation.hiring,software.hiring");
        Company target = company("DE", "Retail", 5000);

        assertTrue(hiring.covers(target, Set.of(), false, Set.of("software.hiring")),
                "any of them is enough — the profile asks whether any of this is happening");
        assertTrue(hiring.covers(target, Set.of(), false, Set.of("automation.hiring", "unrelated.question")));
        assertFalse(hiring.covers(target, Set.of(), false, Set.of("data.governance")),
                "a question nobody asked about does not put a company in this book");
        assertFalse(hiring.covers(target, Set.of(), false, Set.of()),
                "and neither does having no signals at all");
    }

    @Test
    void questionsFilterAndNeverScore() {
        // The predicate takes signals and returns membership. There is no score to
        // touch here, which is the point: a profile is a description, not a
        // weighting, and this is the test that would fail first if that changed.
        IcpProfile withQuestions = profile("DE", "", 0, "automation.hiring");
        IcpProfile without = profile("DE", "", 0);
        Company target = company("DE", "Retail", 5000);

        assertTrue(without.covers(target, withQuestions.namedCountries(), false, Set.of()));
        assertTrue(withQuestions.covers(target, without.namedCountries(), false, Set.of("automation.hiring")));
        assertFalse(withQuestions.covers(target, without.namedCountries(), false, Set.of()));
    }
}
