package com.pistonpunk.gigahack26.domain;

public enum WeightTier {
    HIGH(1.5),
    MEDIUM(0.7),
    LOW(0.3);

    private final double nats;

    WeightTier(double nats) {
        this.nats = nats;
    }

    public double nats() {
        return nats;
    }
}
