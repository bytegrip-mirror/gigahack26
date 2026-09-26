package com.pistonpunk.gigahack26.studio.application;

import java.util.List;
import java.util.UUID;

public record PreviewResponse(
        String serviceLine,
        int matchedCompanies,
        int matchedEvidence,
        int scannedEvidence,
        long tookMs,
        String engine,
        List<Match> matches) {

    public record Match(UUID companyId, String company, int evidence, int signals, double topConfidence, String quote) {
    }
}
