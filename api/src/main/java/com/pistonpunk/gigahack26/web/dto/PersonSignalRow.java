package com.pistonpunk.gigahack26.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PersonSignalRow(
        UUID id,
        String kind,
        String strength,
        String text,
        String sourceUrl,
        String publisher,
        Instant publishedAt) {
}
