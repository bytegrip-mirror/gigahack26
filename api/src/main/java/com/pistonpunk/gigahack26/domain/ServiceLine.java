package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_line")
public class ServiceLine {

    @Id
    private UUID id;

    @Column(name = "line_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 600)
    private String description;

    @Column(name = "value_proposition", length = 600)
    private String valueProposition;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServiceLine() {
    }

    public ServiceLine(UUID id, String key, String name, String description, String valueProposition,
                       int sortOrder, boolean active, Instant createdAt) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.description = description;
        this.valueProposition = valueProposition;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getValueProposition() {
        return valueProposition;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void revise(String name, String description, String valueProposition, int sortOrder, boolean active) {
        this.name = name;
        this.description = description;
        this.valueProposition = valueProposition;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
