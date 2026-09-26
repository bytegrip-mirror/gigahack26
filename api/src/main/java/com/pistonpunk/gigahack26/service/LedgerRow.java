package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.SourceKind;
import com.pistonpunk.gigahack26.domain.Verdict;
import com.pistonpunk.gigahack26.domain.WeightTier;

import java.time.Instant;
import java.util.UUID;

public record LedgerRow(
        UUID evidenceId,
        UUID questionId,
        String questionKey,
        String questionTitle,
        String questionText,
        String theme,
        WeightTier tier,
        Polarity polarity,
        Verdict verdict,
        double confidence,
        int spanStart,
        int spanEnd,
        String spanText,
        String rationale,
        String engine,
        boolean spanVerified,
        String sourceUrl,
        String publisher,
        SourceKind sourceKind,
        String title,
        Instant anchor,
        String anchorKind,
        double ageDays,
        double halfLifeDays,
        double tierNats,
        double authority,
        double decay,
        double shrink,
        double rawNats,
        int groupRank,
        double discount,
        double contributionNats,
        boolean themeCapped,
        boolean blocking) {

    public double odds() {
        return Math.exp(rawNats);
    }

    /**
     * Whether this reading asserts what its question asks. The tier weight is
     * zeroed by {@code PolicyQuestion.magnitudeFor} for a verdict that answers
     * the question by denying it — a company that sells the capability is not a
     * buyer of it — so the weight carries the answer and needs no second field.
     */
    public boolean asserted() {
        return tierNats != 0.0;
    }
}
