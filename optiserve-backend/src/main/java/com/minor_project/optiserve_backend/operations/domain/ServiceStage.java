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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "service_stages")
public class ServiceStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private ServiceWorkflow workflow;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ServiceStageStatus status;

    @Column(name = "eligible_at")
    private Instant eligibleAt;

    @Column(name = "predicted_service_duration", precision = 21, scale = 0)
    private Duration predictedServiceDuration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceStage() {
    }

    private ServiceStage(
            ServiceWorkflow workflow,
            int sequenceNumber,
            ServiceType serviceType,
            Duration predictedServiceDuration) {
        this.workflow = Objects.requireNonNull(workflow, "workflow must not be null");
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        this.sequenceNumber = sequenceNumber;
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType must not be null");
        if (predictedServiceDuration != null
                && (predictedServiceDuration.isNegative() || predictedServiceDuration.isZero())) {
            throw new IllegalArgumentException("predictedServiceDuration must be positive when provided");
        }
        this.predictedServiceDuration = predictedServiceDuration;
        this.status = ServiceStageStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    static ServiceStage create(
            ServiceWorkflow workflow,
            int sequenceNumber,
            ServiceType serviceType,
            Duration predictedServiceDuration) {
        return new ServiceStage(workflow, sequenceNumber, serviceType, predictedServiceDuration);
    }

    public void queue() {
        transitionTo(ServiceStageStatus.QUEUED, null);
    }

    public void assign() {
        transitionTo(ServiceStageStatus.ASSIGNED, null);
    }

    public void start() {
        transitionTo(ServiceStageStatus.IN_PROGRESS, null);
    }

    void complete(Instant completedAt) {
        transitionTo(ServiceStageStatus.COMPLETED, Objects.requireNonNull(completedAt, "completedAt must not be null"));
    }

    void makeEligible(Instant eligibleAt) {
        transitionTo(ServiceStageStatus.ELIGIBLE, Objects.requireNonNull(eligibleAt, "eligibleAt must not be null"));
    }

    void cancel() {
        if (status == ServiceStageStatus.COMPLETED || status == ServiceStageStatus.CANCELLED) {
            throw new IllegalStateException("A completed or cancelled stage cannot be cancelled");
        }
        status = ServiceStageStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ServiceWorkflow getWorkflow() {
        return workflow;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public ServiceStageStatus getStatus() {
        return status;
    }

    public Instant getEligibleAt() {
        return eligibleAt;
    }

    public Duration getPredictedServiceDuration() {
        return predictedServiceDuration;
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

    private void transitionTo(ServiceStageStatus targetStatus, Instant eligibilityTime) {
        if (!isTransitionAllowed(status, targetStatus)) {
            throw new IllegalStateException("Cannot transition service stage from " + status + " to " + targetStatus);
        }
        status = targetStatus;
        if (targetStatus == ServiceStageStatus.ELIGIBLE) {
            eligibleAt = eligibilityTime;
        }
        updatedAt = Instant.now();
    }

    private static boolean isTransitionAllowed(ServiceStageStatus from, ServiceStageStatus to) {
        return switch (from) {
            case PENDING -> to == ServiceStageStatus.ELIGIBLE;
            case ELIGIBLE -> to == ServiceStageStatus.QUEUED || to == ServiceStageStatus.CANCELLED;
            case QUEUED -> to == ServiceStageStatus.ASSIGNED || to == ServiceStageStatus.CANCELLED;
            case ASSIGNED -> to == ServiceStageStatus.IN_PROGRESS || to == ServiceStageStatus.CANCELLED;
            case IN_PROGRESS -> to == ServiceStageStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
