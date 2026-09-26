package com.pistonpunk.gigahack26.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyLibraryTest {

    @Test
    void cuesAreComparedAsASetNotAsASequence() {
        assertTrue(PolicyLibrary.sameTerms(
                List.of("process mining", "shared services", "cost reduction"),
                List.of("cost reduction", "process mining", "shared services")),
                "an element collection has no order column, so a reordered read is not a change");
    }

    @Test
    void aGenuinelyDifferentCueListIsAChange() {
        assertFalse(PolicyLibrary.sameTerms(
                List.of("process mining", "shared services"),
                List.of("process mining", "shared services", "cost reduction")));
        assertFalse(PolicyLibrary.sameTerms(
                List.of("process mining"),
                List.of("process optimization")));
    }

    @Test
    void repeatedCuesAreNotADifference() {
        assertTrue(PolicyLibrary.sameTerms(List.of("ai"), List.of("ai")));
        assertFalse(PolicyLibrary.sameTerms(List.of("ai", "ai"), List.of("ai")));
    }
}
