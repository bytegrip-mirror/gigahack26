package com.pistonpunk.gigahack26.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.corpus")
public record CorpusProperties(
        String rawPath,
        String signalsPath,
        boolean seedOnStartup,
        boolean forceReseed,
        boolean adminEnabled) {
}
