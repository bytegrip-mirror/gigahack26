package com.pistonpunk.gigahack26.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.enrichment")
public record EnrichmentProperties(String provider, String apiKey, String baseUrl) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    public EnrichmentProperties {
        provider = provider == null || provider.isBlank() ? "none" : provider;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "" : baseUrl;
    }
}
