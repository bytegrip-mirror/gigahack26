package com.pistonpunk.gigahack26.board.web;


import com.pistonpunk.gigahack26.scoring.domain.LedgerRow;
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
