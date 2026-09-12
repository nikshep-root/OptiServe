package com.minor_project.optiserve_backend.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @NotBlank
    @Size(max = 32)
    @Column(name = "registration_number", nullable = false, unique = true, length = 32)
    private String registrationNumber;

    @NotBlank
    @Size(max = 100)
    @Column(name = "make", nullable = false, length = 100)
    private String make;

    @NotBlank
    @Size(max = 100)
    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @NotNull
    @Positive
    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {
    }

    private Vehicle(UUID customerId, String registrationNumber, String make, String model, Integer year) {
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.registrationNumber = requireRegistrationNumber(registrationNumber);
        this.make = requireText(make, "make", 100);
        this.model = requireText(model, "model", 100);
        this.year = requirePositiveYear(year);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Vehicle create(UUID customerId, String registrationNumber, String make, String model, Integer year) {
        return new Vehicle(customerId, registrationNumber, make, model, year);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public Integer getYear() {
        return year;
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

    private static String requireRegistrationNumber(String value) {
        return requireText(value, "registrationNumber", 32).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
        return normalized;
    }

    private static Integer requirePositiveYear(Integer value) {
        Objects.requireNonNull(value, "year must not be null");
        if (value <= 0) {
            throw new IllegalArgumentException("year must be positive");
        }
        return value;
    }
}
