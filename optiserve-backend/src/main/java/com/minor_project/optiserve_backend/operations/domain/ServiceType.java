package com.minor_project.optiserve_backend.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "service_types")
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "default_service_duration", nullable = false, precision = 21, scale = 0)
    private Duration defaultServiceDuration;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceType() {
    }

    private ServiceType(String name, String description, Duration defaultServiceDuration) {
        this.name = requireText(name, "name");
        this.description = description;
        this.defaultServiceDuration = requirePositiveDuration(defaultServiceDuration, "defaultServiceDuration");
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static ServiceType create(String name, String description, Duration defaultServiceDuration) {
        return new ServiceType(name, description, defaultServiceDuration);
    }

    public void activate() {
        active = true;
        updatedAt = Instant.now();
    }

    public void deactivate() {
        active = false;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Duration getDefaultServiceDuration() {
        return defaultServiceDuration;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void initializeTimestamps() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static Duration requirePositiveDuration(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
