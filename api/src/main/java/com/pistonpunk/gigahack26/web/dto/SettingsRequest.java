package com.pistonpunk.gigahack26.web.dto;

import jakarta.validation.constraints.Size;

public record SettingsRequest(
        @Size(max = 1000) String icpCountries,
        @Size(max = 1000) String icpIndustries,
        int icpMinEmployees,
        boolean geographyGate,
        boolean sizeGate,
        boolean competitorGate,
        Boolean industryGate,
        double baseRate,
        double priorityThreshold,
        double nurtureThreshold,
        double watchThreshold,
        double rankDiscount,
        @Size(max = 40) String voiceTone,
        @Size(max = 20) String voiceFormality,
        @Size(max = 20) String voiceLength,
        @Size(max = 10) String voiceLanguage,
        @Size(max = 80) String voiceReader,
        @Size(max = 200) String voiceAsk,
        @Size(max = 120) String voiceSignOff,
        @Size(max = 160) String voiceSignature,
        @Size(max = 600) String voiceAvoid,
        @Size(max = 2000) String voiceNotes) {
}
