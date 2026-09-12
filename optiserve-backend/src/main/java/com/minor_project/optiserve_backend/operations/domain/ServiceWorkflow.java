package com.minor_project.optiserve_backend.operations.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "service_workflows")
public class ServiceWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false, unique = true)
    private ServiceRequest serviceRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ServiceWorkflowStatus status;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<ServiceStage> stages = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceWorkflow() {
    }

    private ServiceWorkflow(ServiceRequest serviceRequest) {
        this.serviceRequest = Objects.requireNonNull(serviceRequest, "serviceRequest must not be null");
        this.status = ServiceWorkflowStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static ServiceWorkflow create(ServiceRequest serviceRequest) {
        return new ServiceWorkflow(serviceRequest);
    }

    public ServiceStage addStage(ServiceType serviceType, Duration predictedServiceDuration) {
        if (status != ServiceWorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Stages can only be added to an active workflow");
        }
        ServiceStage stage = ServiceStage.create(this, stages.size() + 1, serviceType, predictedServiceDuration);
        if (stages.isEmpty()) {
            stage.makeEligible(Instant.now());
        }
        stages.add(stage);
        updatedAt = Instant.now();
        return stage;
    }

    public void completeStage(ServiceStage stage, Instant completedAt) {
        requireOwnedStage(stage);
        stage.complete(completedAt);

        ServiceStage nextStage = stages.stream()
                .filter(candidate -> candidate.getSequenceNumber() == stage.getSequenceNumber() + 1)
                .findFirst()
                .orElse(null);
        if (nextStage != null) {
            nextStage.makeEligible(completedAt);
        } else {
            status = ServiceWorkflowStatus.COMPLETED;
        }
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status != ServiceWorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Only an active workflow can be cancelled");
        }
        if (stages.stream().anyMatch(stage -> stage.getStatus() == ServiceStageStatus.IN_PROGRESS)) {
            throw new IllegalStateException("An in-progress stage must not be cancelled");
        }
        stages.stream()
                .filter(stage -> stage.getStatus() != ServiceStageStatus.COMPLETED
                        && stage.getStatus() != ServiceStageStatus.CANCELLED)
                .forEach(ServiceStage::cancel);
        status = ServiceWorkflowStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public ServiceWorkflowStatus getStatus() {
        return status;
    }

    public List<ServiceStage> getStages() {
        return stages.stream()
                .sorted(Comparator.comparingInt(ServiceStage::getSequenceNumber))
                .toList();
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

    private void requireOwnedStage(ServiceStage stage) {
        Objects.requireNonNull(stage, "stage must not be null");
        if (stage.getWorkflow() != this) {
            throw new IllegalArgumentException("Stage does not belong to this workflow");
        }
    }
}
