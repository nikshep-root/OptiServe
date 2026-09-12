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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mechanic_id", nullable = false)
    private Mechanic mechanic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bay_id", nullable = false)
    private ServiceBay serviceBay;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "predicted_service_duration", precision = 21, scale = 0)
    private Duration predictedServiceDuration;

    @Column(name = "actual_service_duration", precision = 21, scale = 0)
    private Duration actualServiceDuration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AssignmentStatus status;

    protected Assignment() {
    }

    private Assignment(
            ServiceRequest serviceRequest,
            Mechanic mechanic,
            ServiceBay serviceBay,
            Instant assignedAt,
            Duration predictedServiceDuration) {
        this.serviceRequest = Objects.requireNonNull(serviceRequest, "serviceRequest must not be null");
        this.mechanic = Objects.requireNonNull(mechanic, "mechanic must not be null");
        this.serviceBay = Objects.requireNonNull(serviceBay, "serviceBay must not be null");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        if (predictedServiceDuration != null && (predictedServiceDuration.isNegative() || predictedServiceDuration.isZero())) {
            throw new IllegalArgumentException("predictedServiceDuration must be positive when provided");
        }
        if (serviceRequest.getStatus() != ServiceRequestStatus.WAITING) {
            throw new IllegalStateException("Only a waiting service request can be assigned");
        }
        if (mechanic.getStatus() != MechanicStatus.AVAILABLE) {
            throw new IllegalStateException("Only an available mechanic can receive an assignment");
        }
        if (serviceBay.getStatus() != BayStatus.AVAILABLE) {
            throw new IllegalStateException("Only an available service bay can receive an assignment");
        }
        if (!mechanic.supports(serviceRequest.getServiceType())) {
            throw new IllegalArgumentException("Mechanic is not qualified for the requested service type");
        }
        if (!serviceBay.supports(serviceRequest.getServiceType())) {
            throw new IllegalArgumentException("Service bay is not compatible with the requested service type");
        }
        this.predictedServiceDuration = predictedServiceDuration;
        this.status = AssignmentStatus.ASSIGNED;
        serviceRequest.assign();
        mechanic.markBusy();
        serviceBay.markOccupied();
    }

    public static Assignment assign(
            ServiceRequest serviceRequest,
            Mechanic mechanic,
            ServiceBay serviceBay,
            Instant assignedAt,
            Duration predictedServiceDuration) {
        return new Assignment(serviceRequest, mechanic, serviceBay, assignedAt, predictedServiceDuration);
    }

    public void start(Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (status != AssignmentStatus.ASSIGNED) {
            throw new IllegalStateException("Only an assigned service can be started");
        }
        if (startedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("startedAt cannot be before assignedAt");
        }
        this.startedAt = startedAt;
        this.status = AssignmentStatus.IN_PROGRESS;
        serviceRequest.startService();
    }

    public void complete(Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (status != AssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress assignment can be completed");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt cannot be before startedAt");
        }
        this.completedAt = completedAt;
        this.actualServiceDuration = Duration.between(startedAt, completedAt);
        this.status = AssignmentStatus.COMPLETED;
        serviceRequest.complete(actualServiceDuration);
        if (mechanic.getStatus() == MechanicStatus.BUSY) {
            mechanic.markAvailable();
        }
        if (serviceBay.getStatus() == BayStatus.OCCUPIED) {
            serviceBay.markAvailable();
        }
    }

    public void cancelBeforeStart() {
        if (status != AssignmentStatus.ASSIGNED) {
            throw new IllegalStateException("An in-progress assignment must not be cancelled");
        }
        status = AssignmentStatus.CANCELLED;
        serviceRequest.cancel();
        if (mechanic.getStatus() == MechanicStatus.BUSY) {
            mechanic.markAvailable();
        }
        if (serviceBay.getStatus() == BayStatus.OCCUPIED) {
            serviceBay.markAvailable();
        }
    }

    public UUID getId() {
        return id;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public Mechanic getMechanic() {
        return mechanic;
    }

    public ServiceBay getServiceBay() {
        return serviceBay;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Duration getPredictedServiceDuration() {
        return predictedServiceDuration;
    }

    public Duration getActualServiceDuration() {
        return actualServiceDuration;
    }

    public AssignmentStatus getStatus() {
        return status;
    }
}
