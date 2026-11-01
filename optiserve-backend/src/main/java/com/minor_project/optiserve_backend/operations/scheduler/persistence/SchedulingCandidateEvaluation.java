package com.minor_project.optiserve_backend.operations.scheduler.persistence;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerCandidateEvaluation;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingCandidateOutcome;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingCandidateReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingFeasibilityStatus;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduling_candidate_evaluations", uniqueConstraints = @UniqueConstraint(
        name = "uq_scheduling_candidate_order", columnNames = {"decision_id", "evaluation_order"}))
public class SchedulingCandidateEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, updatable = false)
    private SchedulingDecision decision;

    @Column(name = "evaluation_order", nullable = false, updatable = false)
    private int evaluationOrder;

    @Column(name = "queue_entry_id", updatable = false)
    private UUID queueEntryId;
    @Column(name = "service_stage_id", updatable = false)
    private UUID serviceStageId;
    @Column(name = "service_request_id", updatable = false)
    private UUID serviceRequestId;
    @Column(name = "workflow_id", updatable = false)
    private UUID workflowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_status", nullable = false, length = 32, updatable = false)
    private QueueEntryStatus queueStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "stage_status", nullable = false, length = 32, updatable = false)
    private ServiceStageStatus stageStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "priority_class", nullable = false, length = 32, updatable = false)
    private PriorityClass priorityClass;
    @Column(name = "queued_at", updatable = false)
    private Instant queuedAt;
    @Column(name = "waiting_age_minutes", nullable = false, updatable = false)
    private long waitingAgeMinutes;
    @Column(name = "appointment_at", updatable = false)
    private Instant appointmentAt;
    @Column(name = "appointment_due", nullable = false, updatable = false)
    private boolean appointmentDue;
    @Column(name = "predicted_service_duration", precision = 21, scale = 0, updatable = false)
    private Duration predictedServiceDuration;
    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_source", length = 32, updatable = false)
    private ServiceDurationPredictionSource predictionSource;
    @Column(name = "critical", nullable = false, updatable = false)
    private boolean critical;
    @Column(name = "base_priority_points", nullable = false, updatable = false)
    private long basePriorityPoints;
    @Column(name = "aging_points", nullable = false, updatable = false)
    private long agingPoints;
    @Column(name = "appointment_bonus_points", nullable = false, updatable = false)
    private long appointmentBonusPoints;
    @Column(name = "duration_bonus_points", nullable = false, updatable = false)
    private long durationBonusPoints;
    @Column(name = "final_score", nullable = false, updatable = false)
    private long finalScore;
    @Enumerated(EnumType.STRING)
    @Column(name = "feasibility_status", nullable = false, length = 48, updatable = false)
    private SchedulingFeasibilityStatus feasibilityStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "observed_resource_status", length = 32, updatable = false)
    private ResourceStatus observedResourceStatus;
    @Column(name = "matched_resource_id", updatable = false)
    private UUID matchedResourceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_outcome", nullable = false, length = 48, updatable = false)
    private SchedulingCandidateOutcome outcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 64, updatable = false)
    private SchedulingCandidateReason reason;

    protected SchedulingCandidateEvaluation() {
    }

    public SchedulingCandidateEvaluation(SchedulingDecision decision, SchedulerCandidateEvaluation snapshot) {
        this.decision = decision;
        this.evaluationOrder = snapshot.evaluationOrder();
        this.queueEntryId = snapshot.queueEntryId();
        this.serviceStageId = snapshot.serviceStageId();
        this.serviceRequestId = snapshot.serviceRequestId();
        this.workflowId = snapshot.workflowId();
        this.queueStatus = snapshot.queueStatus();
        this.stageStatus = snapshot.stageStatus();
        this.priorityClass = snapshot.priorityClass();
        this.queuedAt = snapshot.queuedAt();
        this.waitingAgeMinutes = Math.max(0, snapshot.waitingDuration().toMinutes());
        this.appointmentAt = snapshot.appointmentAt();
        this.appointmentDue = snapshot.appointmentDue();
        this.predictedServiceDuration = snapshot.predictedServiceDuration();
        this.predictionSource = snapshot.predictionSource();
        this.critical = snapshot.critical();
        this.basePriorityPoints = snapshot.basePriorityPoints();
        this.agingPoints = snapshot.agingPoints();
        this.appointmentBonusPoints = snapshot.appointmentBonusPoints();
        this.durationBonusPoints = snapshot.durationBonusPoints();
        this.finalScore = snapshot.finalScore();
        this.feasibilityStatus = snapshot.feasibilityStatus();
        this.observedResourceStatus = snapshot.observedResourceStatus();
        this.matchedResourceId = snapshot.matchedResourceId();
        this.outcome = snapshot.outcome();
        this.reason = snapshot.reason();
    }

    public UUID getId() { return id; }
    public SchedulingDecision getDecision() { return decision; }
    public int getEvaluationOrder() { return evaluationOrder; }
    public UUID getQueueEntryId() { return queueEntryId; }
    public UUID getServiceStageId() { return serviceStageId; }
    public UUID getServiceRequestId() { return serviceRequestId; }
    public UUID getWorkflowId() { return workflowId; }
    public QueueEntryStatus getQueueStatus() { return queueStatus; }
    public ServiceStageStatus getStageStatus() { return stageStatus; }
    public PriorityClass getPriorityClass() { return priorityClass; }
    public Instant getQueuedAt() { return queuedAt; }
    public long getWaitingAgeMinutes() { return waitingAgeMinutes; }
    public Instant getAppointmentAt() { return appointmentAt; }
    public boolean isAppointmentDue() { return appointmentDue; }
    public Duration getPredictedServiceDuration() { return predictedServiceDuration; }
    public ServiceDurationPredictionSource getPredictionSource() { return predictionSource; }
    public boolean isCritical() { return critical; }
    public long getBasePriorityPoints() { return basePriorityPoints; }
    public long getAgingPoints() { return agingPoints; }
    public long getAppointmentBonusPoints() { return appointmentBonusPoints; }
    public long getDurationBonusPoints() { return durationBonusPoints; }
    public long getFinalScore() { return finalScore; }
    public SchedulingFeasibilityStatus getFeasibilityStatus() { return feasibilityStatus; }
    public ResourceStatus getObservedResourceStatus() { return observedResourceStatus; }
    public UUID getMatchedResourceId() { return matchedResourceId; }
    public SchedulingCandidateOutcome getOutcome() { return outcome; }
    public SchedulingCandidateReason getReason() { return reason; }
}
