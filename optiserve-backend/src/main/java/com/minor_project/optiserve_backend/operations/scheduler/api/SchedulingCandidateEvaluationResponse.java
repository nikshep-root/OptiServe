package com.minor_project.optiserve_backend.operations.scheduler.api;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingCandidateOutcome;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingCandidateReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingFeasibilityStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record SchedulingCandidateEvaluationResponse(
        UUID queueEntryId,
        UUID serviceStageId,
        UUID serviceRequestId,
        UUID workflowId,
        int evaluationOrder,
        QueueEntryStatus queueStatus,
        ServiceStageStatus stageStatus,
        PriorityClass priorityClass,
        Instant queuedAt,
        long waitingAgeMinutes,
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
}
