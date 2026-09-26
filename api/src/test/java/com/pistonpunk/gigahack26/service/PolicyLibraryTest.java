package com.pistonpunk.gigahack26.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void theShippedLibraryQuestionsAreUnique() {
        List<String> keys = PolicyLibrary.definitions().stream().map(PolicyLibrary.Definition::key).toList();
        assertEquals(keys.size(), Set.copyOf(keys).size(), "two questions with the same key would overwrite each other");
    }

    @Test
    void noShippedQuestionRemovesACompanyOnAModelReading() {
        List<String> disqualifying = PolicyLibrary.definitions().stream()
                .filter(definition -> definition.polarity() == com.pistonpunk.gigahack26.domain.Polarity.DISQUALIFYING)
                .map(PolicyLibrary.Definition::key)
                .toList();
        assertTrue(disqualifying.isEmpty(),
                "a model's reading must not delete a company the team configured in; vendor exclusion runs on "
                        + "the curated name list instead, and counter-evidence lowers the score: " + disqualifying);
    }

    @Test
    void everyShippedQuestionHasARubricToReasonFrom() {
        List<String> thin = PolicyLibrary.definitions().stream()
                .filter(definition -> definition.instructions() == null || definition.instructions().length() < 80)
                .map(PolicyLibrary.Definition::key)
                .collect(Collectors.toList());
        assertTrue(thin.isEmpty(), "a question without a rubric is a keyword search: " + thin);
    }

    @Test
    void repeatedCuesAreNotADifference() {
        assertTrue(PolicyLibrary.sameTerms(List.of("ai"), List.of("ai")));
        assertFalse(PolicyLibrary.sameTerms(List.of("ai", "ai"), List.of("ai")));
    }
}
