package com.pistonpunk.gigahack26.web.dto;

import jakarta.validation.constraints.Size;

public record SettingsRequest(
        @Size(max = 1000) String icpCountries,
        @Size(max = 1000) String icpIndustries,
        int icpMinEmployees,
        boolean geographyGate,
        boolean sizeGate,
        boolean competitorGate,
        double baseRate,
        double priorityThreshold,
        double nurtureThreshold,
        double watchThreshold,
        double rankDiscount) {
}
