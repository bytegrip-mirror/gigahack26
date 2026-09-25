package com.pistonpunk.gigahack26.domain;

public enum SourceKind {
    JOB_POSTING(0.9),
    NEWS(0.85),
    REGISTRY(1.0),
    FILING(1.0);

    private final double defaultAuthority;

    SourceKind(double defaultAuthority) {
        this.defaultAuthority = defaultAuthority;
    }

    public double defaultAuthority() {
        return defaultAuthority;
    }
}
