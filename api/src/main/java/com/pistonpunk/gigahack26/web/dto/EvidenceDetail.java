package com.pistonpunk.gigahack26.web.dto;

import com.pistonpunk.gigahack26.service.LedgerRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvidenceDetail(
        UUID id,
        String key,
        String company,
        UUID companyId,
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
        String captureId,
        String text,
        List<LedgerRow> signals) {
}
