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
@Table(name = "resources")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ResourceStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "resource_service_type_capabilities",
            joinColumns = @JoinColumn(name = "resource_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "service_type_id", nullable = false))
    private Set<ServiceType> compatibleServiceTypes = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Resource() {
    }

    private Resource(String name, Set<ServiceType> compatibleServiceTypes) {
        this.name = requireText(name, "name");
        this.compatibleServiceTypes.addAll(Objects.requireNonNull(compatibleServiceTypes,
                "compatibleServiceTypes must not be null"));
        this.status = ResourceStatus.AVAILABLE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Resource create(String name, Set<ServiceType> compatibleServiceTypes) {
        return new Resource(name, compatibleServiceTypes);
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
        status = ResourceStatus.AVAILABLE;
        updatedAt = Instant.now();
    }

    public void markBusy() {
        if (status != ResourceStatus.AVAILABLE) {
            throw new IllegalStateException("Only an available resource can become busy");
        }
        status = ResourceStatus.BUSY;
        updatedAt = Instant.now();
    }

    public void markOffline() {
        status = ResourceStatus.OFFLINE;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ResourceStatus getStatus() {
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
