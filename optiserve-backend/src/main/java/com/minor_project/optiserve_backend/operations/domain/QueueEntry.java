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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "queue_entries")
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QueueEntryStatus status;

    protected QueueEntry() {
    }

    private QueueEntry(ServiceRequest serviceRequest, Instant queuedAt) {
        this.serviceRequest = Objects.requireNonNull(serviceRequest, "serviceRequest must not be null");
        this.queuedAt = Objects.requireNonNull(queuedAt, "queuedAt must not be null");
        serviceRequest.enqueue();
        this.status = QueueEntryStatus.WAITING;
    }

    public static QueueEntry enter(ServiceRequest serviceRequest, Instant queuedAt) {
        return new QueueEntry(serviceRequest, queuedAt);
    }

    public void remove() {
        if (status != QueueEntryStatus.WAITING) {
            throw new IllegalStateException("Only a waiting queue entry can be removed");
        }
        status = QueueEntryStatus.REMOVED;
    }

    public UUID getId() {
        return id;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public QueueEntryStatus getStatus() {
        return status;
    }
}
