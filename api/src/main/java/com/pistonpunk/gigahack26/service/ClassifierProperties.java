package com.pistonpunk.gigahack26.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.classifier")
public record ClassifierProperties(
        String engine,
        int maxCandidatesPerDocument,
        double confidenceFloor,
        DeepSeek deepseek) {

    public record DeepSeek(String baseUrl, String apiKey, String model, int concurrency, int timeoutMs) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
