package com.pistonpunk.gigahack26.scoring.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.refresh")
public record RefreshProperties(boolean enabled, long intervalMs, long initialDelayMs) {
}
