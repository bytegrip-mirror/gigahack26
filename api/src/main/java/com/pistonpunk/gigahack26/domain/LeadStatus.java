package com.pistonpunk.gigahack26.domain;

public enum LeadStatus {
    NEW("New", 0),
    CONTACTED("Contacted", 1),
    REPLIED("Replied", 2),
    QUALIFIED("Qualified", 3),
    REJECTED("Rejected", -1);

    private final String label;
    private final int order;

    LeadStatus(String label, int order) {
        this.label = label;
        this.order = order;
    }

    public String label() {
        return label;
    }

    public int order() {
        return order;
    }

    public boolean isOpen() {
        return this == NEW || this == CONTACTED || this == REPLIED;
    }
}
