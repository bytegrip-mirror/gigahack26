package com.pistonpunk.gigahack26.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonRow(
        UUID id,
        UUID companyId,
        String company,
        String domain,
        String name,
        String role,
        String band,
        double probability,
        String serviceLine,
        String sourceUrl,
        String publisher,
        Instant publishedAt,
        List<String> profiles) {
}
