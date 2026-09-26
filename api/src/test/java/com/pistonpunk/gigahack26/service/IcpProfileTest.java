package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.IcpProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcpProfileTest {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private static IcpProfile profile(String countries, String industries, int minEmployees) {
        return new IcpProfile(UUID.randomUUID(), "Test", countries, industries, minEmployees, true, 0, NOW);
    }

    private static Company company(String country, String industry, Integer employees) {
        return new Company(UUID.randomUUID(), "co", "Test Co", "test.example", country, "Berlin",
                industry, "1001-5000", employees, 0.9, null, false, null, NOW);
    }

    @Test
    void aCountryCodeMatchesWhateverCaseItIsWrittenIn() {
        IcpProfile dach = profile("DE,AT,CH", "", 0);
        assertTrue(dach.covers(company("DE", "Automotive", 5000)),
                "the profile stores codes folded and the company stores them shouted");
        assertFalse(dach.covers(company("FR", "Automotive", 5000)));
    }

    @Test
    void anEmptyRuleMeansEverywhere() {
        IcpProfile anywhere = profile("", "", 0);
        assertTrue(anywhere.covers(company("FR", "Retail", null)));
    }

    @Test
    void industryAndSizeHaveToHoldToo() {
        IcpProfile narrow = profile("DE", "Manufacturing,Automotive", 1000);
        assertTrue(narrow.covers(company("DE", "automotive", 5000)));
        assertFalse(narrow.covers(company("DE", "Retail", 5000)));
        assertFalse(narrow.covers(company("DE", "Automotive", 400)));
    }

    @Test
    void anUnknownHeadcountIsNotADisqualification() {
        IcpProfile floor = profile("DE", "", 1000);
        assertTrue(floor.covers(company("DE", "Automotive", null)),
                "a company we have no headcount for is not a company below the floor");
    }
}
