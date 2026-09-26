package com.pistonpunk.gigahack26.policy.domain;

public enum Verdict {
    BUY,
    COUNTER,
    BUILD,
    NONE;

    public static Verdict parse(String raw) {
        if (raw == null) {
            return NONE;
        }
        return switch (raw.trim().toUpperCase().replace('-', '_')) {
            case "BUY", "MATCH", "YES", "TRUE", "POSITIVE" -> BUY;
            case "COUNTER", "NEGATIVE", "AGAINST" -> COUNTER;
            case "BUILD", "IN_HOUSE", "INSOURCE", "COMPETITOR" -> BUILD;
            default -> NONE;
        };
    }
}
