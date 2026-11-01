package com.minor_project.optiserve_backend.operations.scheduler.domain;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Immutable scheduler snapshot used for ranking and persisted decision explanations. */
public record SchedulerCandidateEvaluation(
        QueueEntry queueEntry,
        int evaluationOrder,
        QueueEntryStatus queueStatus,
        ServiceStageStatus stageStatus,
        PriorityClass priorityClass,
        Instant queuedAt,
        Duration waitingDuration,
        Instant appointmentAt,
        boolean appointmentDue,
        Duration predictedServiceDuration,
        ServiceDurationPredictionSource predictionSource,
        boolean critical,
        long basePriorityPoints,
        long agingPoints,
        long appointmentBonusPoints,
        long durationBonusPoints,
        long finalScore,
        SchedulingFeasibilityStatus feasibilityStatus,
        ResourceStatus observedResourceStatus,
        UUID matchedResourceId,
        SchedulingCandidateOutcome outcome,
        SchedulingCandidateReason reason) {

    public static SchedulerCandidateEvaluation legacy(QueueEntry entry, int evaluationOrder) {
        return new SchedulerCandidateEvaluation(
                entry,
                evaluationOrder,
                entry.getStatus(),
                entry.getServiceStage().getStatus(),
                entry.getServiceRequest().getPriorityClass(),
                entry.getQueuedAt(),
                Duration.ZERO,
                entry.getServiceRequest().getAppointmentAt(),
                false,
                entry.getServiceStage().getPredictedServiceDuration(),
                entry.getServiceStage().getPredictionSource(),
                entry.getServiceRequest().getPriorityClass() == PriorityClass.CRITICAL,
                0, 0, 0, 0, 0,
                SchedulingFeasibilityStatus.NOT_EVALUATED,
                null,
                null,
                SchedulingCandidateOutcome.NOT_EVALUATED,
                SchedulingCandidateReason.OTHER_VALIDATED_REASON);
    }

    public UUID queueEntryId() {
        return queueEntry == null ? null : queueEntry.getId();
    }

    public UUID serviceStageId() {
        return queueEntry == null || queueEntry.getServiceStage() == null
                ? null : queueEntry.getServiceStage().getId();
    }

    public UUID serviceRequestId() {
        return queueEntry == null || queueEntry.getServiceRequest() == null
                ? null : queueEntry.getServiceRequest().getId();
    }

    public UUID workflowId() {
        return queueEntry == null || queueEntry.getServiceStage() == null
                || queueEntry.getServiceStage().getWorkflow() == null
                ? null : queueEntry.getServiceStage().getWorkflow().getId();
    }

    public SchedulerCandidateEvaluation withDecision(
            SchedulingFeasibilityStatus feasibilityStatus,
            ResourceStatus observedResourceStatus,
            UUID matchedResourceId,
            SchedulingCandidateOutcome outcome,
            SchedulingCandidateReason reason) {
        return new SchedulerCandidateEvaluation(
                queueEntry, evaluationOrder, queueStatus, stageStatus, priorityClass, queuedAt,
                waitingDuration, appointmentAt, appointmentDue, predictedServiceDuration,
                predictionSource, critical, basePriorityPoints, agingPoints, appointmentBonusPoints,
                durationBonusPoints, finalScore, feasibilityStatus, observedResourceStatus,
                matchedResourceId, outcome, reason);
    }
}
