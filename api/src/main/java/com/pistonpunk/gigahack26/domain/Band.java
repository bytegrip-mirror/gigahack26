package com.pistonpunk.gigahack26.domain;

public enum Band {
    PRIORITY("Priority", "Priority"),
    NURTURE("Nurture", "Nurture"),
    WATCH("Watch", "Watch"),
    THIN("Insufficient evidence", "Thin"),
    DISQUALIFIED("Disqualified", "Rejected");

    private final String label;
    private final String shortLabel;

    Band(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }
}
