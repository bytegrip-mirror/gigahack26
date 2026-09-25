package com.pistonpunk.gigahack26.web.dto;

import com.pistonpunk.gigahack26.domain.DecayClass;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.WeightTier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicyView(
        UUID id,
        String key,
        String serviceLine,
        String text,
        String instructions,
        WeightTier weightTier,
        double nats,
        Polarity polarity,
        String theme,
        DecayClass decayClass,
        int halfLifeDays,
        List<String> cues,
        List<String> counterCues,
        boolean active,
        long signalCount,
        Instant createdAt,
        Instant updatedAt) {
}
