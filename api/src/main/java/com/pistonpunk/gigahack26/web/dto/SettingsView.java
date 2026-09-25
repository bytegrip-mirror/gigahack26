package com.pistonpunk.gigahack26.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SettingsView(
        List<String> icpCountries,
        List<String> icpIndustries,
        int icpMinEmployees,
        boolean geographyGate,
        boolean sizeGate,
        boolean competitorGate,
        double baseRate,
        double priorityThreshold,
        double nurtureThreshold,
        double watchThreshold,
        double rankDiscount,
        double themeCap,
        Instant updatedAt,
        Map<String, Long> inScope,
        long indexedCompanies) {
}
