package com.pistonpunk.gigahack26.scoring.domain;


import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ScoreComputation(
        double probability,
        double logit,
        double nats,
        Band band,
        String gateReason,
        String gateLine,
        List<LedgerRow> rows,
        Map<String, Double> themeTotals,
        int signalCount,
        int themeCount,
        int scannedDocuments,
        int silentDocuments,
        String weakestLink,
        Double priorityHorizonDays,
        Double nurtureHorizonDays,
        Map<String, Double> sensitivity) {

    public LedgerRow strongest() {
        return rows.stream()
                .filter(row -> !row.blocking())
                .max(Comparator.comparingDouble(a -> Math.abs(a.contributionNats())))
                .orElse(null);
    }

    public List<LedgerRow> counterEvidence() {
        return rows.stream()
                .filter(row -> row.contributionNats() < 0 || (!row.blocking() && !row.asserted()))
                .toList();
    }
}
