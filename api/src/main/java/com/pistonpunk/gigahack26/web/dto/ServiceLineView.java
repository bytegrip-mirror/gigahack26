package com.pistonpunk.gigahack26.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ServiceLineView(
        UUID id,
        String key,
        String name,
        String description,
        String valueProposition,
        int sortOrder,
        boolean active,
        long questionCount,
        Instant createdAt) {
}
