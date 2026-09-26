package com.pistonpunk.gigahack26.studio.application;

import com.pistonpunk.gigahack26.policy.domain.DecayClass;
import com.pistonpunk.gigahack26.policy.domain.Polarity;
import com.pistonpunk.gigahack26.policy.domain.WeightTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PolicyRequest(
        @Size(max = 80) String key,
        @NotBlank @Size(max = 80) String serviceLine,
        @Size(max = 200) String title,
        @NotBlank @Size(max = 400) String text,
        @Size(max = 4000) String instructions,
        WeightTier weightTier,
        Polarity polarity,
        @Size(max = 40) String theme,
        DecayClass decayClass,
        List<String> cues,
        List<String> counterCues,
        Boolean active) {
}
