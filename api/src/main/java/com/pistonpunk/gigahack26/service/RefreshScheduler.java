package com.pistonpunk.gigahack26.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshScheduler.class);

    private final ScoreboardService scoreboard;
    private final RefreshProperties properties;
    private final Clock clock;

    private volatile Instant lastRunAt;
    private volatile Instant nextRunAt;
    private volatile long lastRunMs;
    private volatile int lastCompanies;

    public RefreshScheduler(ScoreboardService scoreboard, RefreshProperties properties, Clock clock) {
        this.scoreboard = scoreboard;
        this.properties = properties;
        this.clock = clock;
        this.nextRunAt = clock.instant().plusMillis(properties.intervalMs());
    }

    @Scheduled(fixedDelayString = "${app.refresh.interval-ms:900000}", initialDelayString = "${app.refresh.initial-delay-ms:120000}")
    public void refresh() {
        if (!properties.enabled()) {
            return;
        }
        long started = System.nanoTime();
        try {
            int scored = 0;
            for (String serviceLine : scoreboard.serviceLines()) {
                scoreboard.rescore(serviceLine);
                scored++;
            }
            lastCompanies = scored;
            log.info("scheduled refresh re-scored {} service lines in {} ms", scored,
                    (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("scheduled refresh failed: {}", e.toString());
        } finally {
            lastRunMs = (System.nanoTime() - started) / 1_000_000;
            lastRunAt = clock.instant();
            nextRunAt = lastRunAt.plusMillis(properties.intervalMs());
        }
    }

    public Instant nextRunAt() {
        if (!properties.enabled()) {
            return null;
        }
        return nextRunAt;
    }

    public Instant lastRunAt() {
        return lastRunAt;
    }

    public long lastRunMs() {
        return lastRunMs;
    }

    public int serviceLinesRefreshed() {
        return lastCompanies;
    }

    public Duration interval() {
        return Duration.ofMillis(properties.intervalMs());
    }
}
