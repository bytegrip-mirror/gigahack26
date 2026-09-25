package com.pistonpunk.gigahack26.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public final class CorpusFiles {

    private CorpusFiles() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCorpus(String generatedAt, List<RawCompany> companies, List<RawEvidence> evidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCompany(String key, String name, String domain, String country, String hqCity,
                             String industry, String sizeBand, Integer employees, Double sizeConfidence,
                             String caemCode, Boolean isCompetitor, String coverageNotes, List<String> aliases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawEvidence(String key, String companyKey, String sourceUrl, String publisher,
                              String sourceKind, String publishedAt, String observedAt, String title,
                              String text, String contentHash) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignalOverlay(String engine, String generatedAt, Double strikeRate, Integer pairsSeen,
                                List<OverlaySignal> signals) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverlaySignal(String evidenceKey, String questionKey, String verdict, Double confidence,
                                String quote, String rationale) {
    }
}
