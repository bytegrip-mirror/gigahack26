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

    private static IcpProfile profile(String countries, String industries, int minEmployees) {
        return new IcpProfile(UUID.randomUUID(), "Test", countries, "", industries, minEmployees,
                "", false, false, true, 0, NOW);
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
        assertTrue(dach.covers(company("DE", "Automotive", 5000), dach.namedCountries(), false),
                "the profile stores codes folded and the company stores them shouted");
        assertFalse(dach.covers(company("FR", "Automotive", 5000), dach.namedCountries(), false));
    }

    @Test
    void anEmptyRuleMeansEverywhere() {
        IcpProfile anywhere = profile("", "", 0);
        assertTrue(anywhere.covers(company("FR", "Retail", null), anywhere.namedCountries(), false));
    }

    @Test
    void industryAndSizeHaveToHoldToo() {
        IcpProfile narrow = profile("DE", "Manufacturing,Automotive", 1000);
        assertTrue(narrow.covers(company("DE", "automotive", 5000), narrow.namedCountries(), false));
        assertFalse(narrow.covers(company("DE", "Retail", 5000), narrow.namedCountries(), false));
        assertFalse(narrow.covers(company("DE", "Automotive", 400), narrow.namedCountries(), false));
    }

    @Test
    void anUnknownHeadcountIsNotADisqualification() {
        IcpProfile floor = profile("DE", "", 1000);
        assertTrue(floor.covers(company("DE", "Automotive", null), floor.namedCountries(), false),
                "a company we have no headcount for is not a company below the floor");
    }

    @Test
    void aRegionCoversTheCountriesItExpandsToEvenThoughTheProfileNeverNamedThem() {
        IcpProfile nordics = profile("", "", 0);
        assertTrue(nordics.covers(company("SE", "Retail", 5000), here("SE", "NO", "DK", "FI", "IS"), false),
                "the profile picked a region; the taxonomy is what knows what that means");
        assertFalse(nordics.covers(company("DE", "Retail", 5000), here("SE", "NO", "DK", "FI", "IS"), false));
    }

    @Test
    void aSizeBandIsASecondWayToSayHowBig() {
        IcpProfile large = new IcpProfile(UUID.randomUUID(), "Large", "", "", "", 0,
                "10001+", false, false, true, 0, NOW);
        assertTrue(large.covers(company("DE", "Retail", 20000, "10001+", false), large.namedCountries(), false));
        assertFalse(large.covers(company("DE", "Retail", 3000, "1001-5000", false), large.namedCountries(), false));
    }

    @Test
    void aVendorCanBeKeptOutOfOneBookWithoutBeingKeptOutOfTheBoard() {
        IcpProfile ours = new IcpProfile(UUID.randomUUID(), "Ours", "", "", "", 0,
                "", true, false, true, 0, NOW);
        assertFalse(ours.covers(company("DE", "IT Services", 5000, "1001-5000", true), ours.namedCountries(), false));
        assertTrue(ours.covers(company("DE", "IT Services", 5000, "1001-5000", false), ours.namedCountries(), false));
    }

    @Test
    void untouchedOnlyLeavesOutTheOnesSomebodyHasAlreadyRung() {
        IcpProfile fresh = new IcpProfile(UUID.randomUUID(), "Fresh", "", "", "", 0,
                "", false, true, true, 0, NOW);
        assertTrue(fresh.covers(company("DE", "Retail", 5000), fresh.namedCountries(), false));
        assertFalse(fresh.covers(company("DE", "Retail", 5000), fresh.namedCountries(), true));
    }
}
