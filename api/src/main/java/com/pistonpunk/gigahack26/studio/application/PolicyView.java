package com.pistonpunk.gigahack26.studio.application;


import com.pistonpunk.gigahack26.policy.domain.DecayClass;
import com.pistonpunk.gigahack26.policy.domain.Polarity;
import com.pistonpunk.gigahack26.policy.domain.WeightTier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicyView(
        UUID id,
        String key,
        String title,
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
