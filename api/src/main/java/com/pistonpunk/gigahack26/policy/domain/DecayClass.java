package com.pistonpunk.gigahack26.policy.domain;

public enum DecayClass {
    EVENT(30),
    HIRING(90),
    STRUCTURAL(180);

    private final int halfLifeDays;

    DecayClass(int halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
    }

    public int halfLifeDays() {
        return halfLifeDays;
    }
}
