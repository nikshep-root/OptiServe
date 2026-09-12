package com.minor_project.optiserve_backend.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "service_bays")
public class ServiceBay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bay_number", nullable = false, unique = true, length = 32)
    private String bayNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "bay_type", nullable = false, length = 32)
    private BayType bayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BayStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "bay_service_type_capabilities",
            joinColumns = @JoinColumn(name = "service_bay_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "service_type_id", nullable = false))
    private Set<ServiceType> compatibleServiceTypes = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceBay() {
    }

    private ServiceBay(String bayNumber, BayType bayType, Set<ServiceType> compatibleServiceTypes) {
        this.bayNumber = requireText(bayNumber, "bayNumber").toUpperCase();
        this.bayType = Objects.requireNonNull(bayType, "bayType must not be null");
        if (compatibleServiceTypes != null) {
            this.compatibleServiceTypes.addAll(compatibleServiceTypes);
        }
        this.status = BayStatus.AVAILABLE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static ServiceBay create(String bayNumber, BayType bayType, Set<ServiceType> compatibleServiceTypes) {
        return new ServiceBay(bayNumber, bayType, compatibleServiceTypes);
    }

    public void addCapability(ServiceType serviceType) {
        compatibleServiceTypes.add(Objects.requireNonNull(serviceType, "serviceType must not be null"));
        updatedAt = Instant.now();
    }

    public void removeCapability(ServiceType serviceType) {
        compatibleServiceTypes.remove(Objects.requireNonNull(serviceType, "serviceType must not be null"));
        updatedAt = Instant.now();
    }

    public boolean supports(ServiceType serviceType) {
        return compatibleServiceTypes.contains(Objects.requireNonNull(serviceType, "serviceType must not be null"));
    }

    public void markAvailable() {
        status = BayStatus.AVAILABLE;
        updatedAt = Instant.now();
    }

    public void markOccupied() {
        if (status != BayStatus.AVAILABLE) {
            throw new IllegalStateException("Only an available service bay can become occupied");
        }
        status = BayStatus.OCCUPIED;
        updatedAt = Instant.now();
    }

    public void markMaintenance() {
        status = BayStatus.MAINTENANCE;
        updatedAt = Instant.now();
    }

    public void markOffline() {
        status = BayStatus.OFFLINE;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getBayNumber() {
        return bayNumber;
    }

    public BayType getBayType() {
        return bayType;
    }

    public BayStatus getStatus() {
        return status;
    }

    public Set<ServiceType> getCompatibleServiceTypes() {
        return Set.copyOf(compatibleServiceTypes);
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
