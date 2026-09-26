package com.pistonpunk.gigahack26.web.dto;

import com.pistonpunk.gigahack26.service.LedgerRow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CompanyDetail(
        UUID id,
        String name,
        String domain,
        String country,
        String hqCity,
        String industry,
        String sizeBand,
        Integer employees,
        Double sizeConfidence,
        String caemCode,
        boolean competitor,
        List<BelowFloor> belowFloor,
        List<String> aliases,
        String coverageNotes,
        String serviceLine,
        double probability,
        double logit,
        double nats,
        String band,
        String bandLabel,
        String gateReason,
        String gateLine,
        double baseRate,
        double baseLogit,
        double themeCap,
        double priorityThreshold,
        double nurtureThreshold,
        double watchThreshold,
        int signalCount,
        int themeCount,
        int scannedDocuments,
        int silentDocuments,
        List<LedgerRow> ledger,
        List<LedgerRow> counterEvidence,
        List<Theme> themes,
        List<String> profiles,
        String weakestLink,
        Double priorityHorizonDays,
        Double nurtureHorizonDays,
        Map<String, Double> sensitivity,
        List<EvidenceView> evidence,
        Instant computedAt,
        String engine,
        WhyNow whyNow,
        LeadView lead,
        boolean crmConfigured) {

    public record Theme(String name, double total, boolean capped, int rows) {
    }

    public record EvidenceView(
            UUID id,
            String title,
            String sourceUrl,
            String publisher,
            String sourceKind,
            double authority,
            Instant publishedAt,
            Instant observedAt,
            String anchorKind,
            int charLength,
            String contentHash,
            int signalCount,
            String verdict,
            Double contribution,
            String questionText) {
    }

    /** A passage the engine read, scored below the floor, and did not count. */
    public record BelowFloor(
            String evidenceId,
            String questionKey,
            String questionTitle,
            String polarity,
            double confidence,
            int spanStart,
            int spanEnd,
            String spanText,
            String rationale) {
    }
}
