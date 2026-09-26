package com.pistonpunk.gigahack26.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scoring")
public record ScoringProperties(
        double baseRate,
        double priorityThreshold,
        double nurtureThreshold,
        double watchThreshold,
        double rankDiscount,
        int icpMinEmployees,
        String icpCountries,
        boolean geographyGate,
        boolean sizeGate,
        boolean competitorGate,
        boolean industryGate,
        String icpIndustries) {

    public double baseLogit() {
        return Math.log(baseRate / (1.0 - baseRate));
    }

    public double themeCap() {
        return priorityLogit() - baseLogit();
    }

    public double priorityLogit() {
        return Math.log(priorityThreshold / (1.0 - priorityThreshold));
    }
}
