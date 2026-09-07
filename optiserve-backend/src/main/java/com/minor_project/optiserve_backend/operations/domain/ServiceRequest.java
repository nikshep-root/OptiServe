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
@Table(name = "service_requests")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_class", nullable = false, length = 32)
    private PriorityClass priorityClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ServiceRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "appointment_at")
    private Instant appointmentAt;

    @Column(name = "actual_service_duration", precision = 21, scale = 0)
    private Duration actualServiceDuration;

    protected ServiceRequest() {
    }

    private ServiceRequest(ServiceType serviceType, PriorityClass priorityClass, Instant appointmentAt) {
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType must not be null");
        this.priorityClass = Objects.requireNonNull(priorityClass, "priorityClass must not be null");
        if (priorityClass == PriorityClass.APPOINTMENT && appointmentAt == null) {
            throw new IllegalArgumentException("appointmentAt is required for an appointment request");
        }
        this.appointmentAt = appointmentAt;
        this.status = ServiceRequestStatus.CREATED;
        this.requestedAt = Instant.now();
    }

    public static ServiceRequest create(
            ServiceType serviceType,
            PriorityClass priorityClass,
            Instant appointmentAt) {
        return new ServiceRequest(serviceType, priorityClass, appointmentAt);
    }

    public void enqueue() {
        transitionTo(ServiceRequestStatus.WAITING);
    }

    public void assign() {
        transitionTo(ServiceRequestStatus.ASSIGNED);
    }

    public void startService() {
        transitionTo(ServiceRequestStatus.IN_SERVICE);
    }

    public void complete(Duration actualServiceDuration) {
        Objects.requireNonNull(actualServiceDuration, "actualServiceDuration must not be null");
        if (actualServiceDuration.isNegative()) {
            throw new IllegalArgumentException("actualServiceDuration must not be negative");
        }
        transitionTo(ServiceRequestStatus.COMPLETED);
        this.actualServiceDuration = actualServiceDuration;
    }

    public void cancel() {
        transitionTo(ServiceRequestStatus.CANCELLED);
    }

    public void markNoShow() {
        transitionTo(ServiceRequestStatus.NO_SHOW);
    }

    public UUID getId() {
        return id;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public PriorityClass getPriorityClass() {
        return priorityClass;
    }

    public ServiceRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAppointmentAt() {
        return appointmentAt;
    }

    public Duration getActualServiceDuration() {
        return actualServiceDuration;
    }

    @PrePersist
    void initializeRequestedAt() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    @PreUpdate
    void validateStatus() {
        Objects.requireNonNull(status, "status must not be null");
    }

    private void transitionTo(ServiceRequestStatus targetStatus) {
        if (!isTransitionAllowed(status, targetStatus)) {
            throw new IllegalStateException("Cannot transition service request from " + status + " to " + targetStatus);
        }
        status = targetStatus;
    }

    private static boolean isTransitionAllowed(ServiceRequestStatus from, ServiceRequestStatus to) {
        return switch (from) {
            case CREATED -> to == ServiceRequestStatus.WAITING || to == ServiceRequestStatus.CANCELLED;
            case WAITING -> to == ServiceRequestStatus.ASSIGNED
                    || to == ServiceRequestStatus.CANCELLED
                    || to == ServiceRequestStatus.NO_SHOW;
            case ASSIGNED -> to == ServiceRequestStatus.WAITING
                    || to == ServiceRequestStatus.IN_SERVICE
                    || to == ServiceRequestStatus.CANCELLED
                    || to == ServiceRequestStatus.NO_SHOW;
            case IN_SERVICE -> to == ServiceRequestStatus.COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
