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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "mechanics")
public class Mechanic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false, unique = true, length = 64)
    private String employeeId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MechanicStatus status;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "mechanic_service_type_capabilities",
            joinColumns = @JoinColumn(name = "mechanic_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "service_type_id", nullable = false))
    private Set<ServiceType> compatibleServiceTypes = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Mechanic() {
    }

    private Mechanic(
            String employeeId,
            String name,
            String phone,
            LocalDate joinedAt,
            Set<ServiceType> compatibleServiceTypes) {
        this.employeeId = requireText(employeeId, "employeeId");
        this.name = requireText(name, "name");
        this.phone = phone != null && !phone.isBlank() ? phone.trim() : null;
        this.joinedAt = joinedAt;
        if (compatibleServiceTypes != null) {
            this.compatibleServiceTypes.addAll(compatibleServiceTypes);
        }
        this.status = MechanicStatus.AVAILABLE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Mechanic create(
            String employeeId,
            String name,
            String phone,
            LocalDate joinedAt,
            Set<ServiceType> compatibleServiceTypes) {
        return new Mechanic(employeeId, name, phone, joinedAt, compatibleServiceTypes);
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
        status = MechanicStatus.AVAILABLE;
        updatedAt = Instant.now();
    }

    public void markBusy() {
        if (status != MechanicStatus.AVAILABLE) {
            throw new IllegalStateException("Only an available mechanic can become busy");
        }
        status = MechanicStatus.BUSY;
        updatedAt = Instant.now();
    }

    public void markOnLeave() {
        status = MechanicStatus.ON_LEAVE;
        updatedAt = Instant.now();
    }

    public void markOffline() {
        status = MechanicStatus.OFFLINE;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public MechanicStatus getStatus() {
        return status;
    }

    public LocalDate getJoinedAt() {
        return joinedAt;
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
