package com.pistonpunk.gigahack26.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Readiness {

    private final AtomicBoolean corpusSeeded = new AtomicBoolean(false);
    private final AtomicBoolean seeding = new AtomicBoolean(false);

    public void seeding(boolean running) {
        seeding.set(running);
        if (running) {
            corpusSeeded.set(false);
        }
    }

    public void seeded() {
        seeding.set(false);
        corpusSeeded.set(true);
    }

    public boolean ready() {
        return corpusSeeded.get();
    }

    public boolean seeding() {
        return seeding.get();
    }
}
