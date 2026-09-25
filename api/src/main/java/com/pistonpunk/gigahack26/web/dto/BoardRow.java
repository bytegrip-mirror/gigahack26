package com.pistonpunk.gigahack26.web.dto;

import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.WeightTier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BoardRow(
        UUID id,
        int rank,
        String name,
        String domain,
        String country,
        String hqCity,
        String industry,
        String sizeBand,
        boolean competitor,
        double probability,
        String band,
        String bandLabel,
        String gateReason,
        double nats,
        int signalCount,
        int themeCount,
        int evidenceCount,
        int scannedDocuments,
        int silentDocuments,
        Instant newestSignalAt,
        Double newestSignalAgeDays,
        Chip strongest,
        List<Chip> chips,
        List<String> themes,
        String headline) {

    public record Chip(
            String evidenceId,
            String questionKey,
            String question,
            String theme,
            WeightTier tier,
            Polarity polarity,
            double contribution,
            String quote,
            int spanStart,
            int spanEnd,
            String sourceUrl,
            String publisher) {
    }
}
