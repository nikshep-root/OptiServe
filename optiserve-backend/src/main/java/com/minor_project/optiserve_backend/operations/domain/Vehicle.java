package com.minor_project.optiserve_backend.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "registration_number", nullable = false, unique = true, length = 32)
    private String registrationNumber;

    @Column(name = "make", nullable = false, length = 100)
    private String make;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "manufacturing_year")
    private Integer manufacturingYear;

    @Column(name = "fuel_type", length = 32)
    private String fuelType;

    @Column(name = "color", length = 64)
    private String color;

    @Column(name = "vin", unique = true, length = 64)
    private String vin;

    @Column(name = "current_mileage")
    private Long currentMileage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {
    }

    private Vehicle(
            Customer customer,
            String registrationNumber,
            String make,
            String model,
            Integer manufacturingYear,
            String fuelType,
            String color,
            String vin,
            Long currentMileage) {
        this.customer = customer;
        this.registrationNumber = requireText(registrationNumber, "registrationNumber").toUpperCase();
        this.make = requireText(make, "make");
        this.model = requireText(model, "model");
        if (manufacturingYear != null && (manufacturingYear < 1886 || manufacturingYear > 2100)) {
            throw new IllegalArgumentException("manufacturingYear must be between 1886 and 2100");
        }
        if (currentMileage != null && currentMileage < 0) {
            throw new IllegalArgumentException("currentMileage must not be negative");
        }
        this.manufacturingYear = manufacturingYear;
        this.fuelType = fuelType != null && !fuelType.isBlank() ? fuelType.trim().toUpperCase() : null;
        this.color = color != null && !color.isBlank() ? color.trim() : null;
        this.vin = vin != null && !vin.isBlank() ? vin.trim().toUpperCase() : null;
        this.currentMileage = currentMileage != null ? currentMileage : 0L;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Vehicle create(
            Customer customer,
            String registrationNumber,
            String make,
            String model,
            Integer manufacturingYear,
            String fuelType,
            String color,
            String vin,
            Long currentMileage) {
        return new Vehicle(customer, registrationNumber, make, model, manufacturingYear, fuelType, color, vin, currentMileage);
    }

    public void updateMileage(long newMileage) {
        if (newMileage < 0) {
            throw new IllegalArgumentException("Mileage must not be negative");
        }
        if (currentMileage != null && newMileage < currentMileage) {
            throw new IllegalArgumentException("New mileage cannot be less than current recorded mileage");
        }
        this.currentMileage = newMileage;
        this.updatedAt = Instant.now();
    }

    public void assignOwner(Customer customer) {
        this.customer = customer;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
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

    public Integer getManufacturingYear() {
        return manufacturingYear;
    }

    public String getFuelType() {
        return fuelType;
    }

    public String getColor() {
        return color;
    }

    public String getVin() {
        return vin;
    }

    public Long getCurrentMileage() {
        return currentMileage;
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
}
