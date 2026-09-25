package com.pistonpunk.gigahack26.domain;

public enum Band {
    PRIORITY("Priority"),
    NURTURE("Nurture"),
    WATCH("Watch"),
    THIN("Insufficient evidence"),
    DISQUALIFIED("Disqualified");

    private final String label;

    Band(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
