package com.minor_project.optiserve_backend.operations.scheduler.domain;

import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Selects a queue candidate without changing queue, stage, resource, or assignment state. */
public interface SchedulerStrategy {

    Optional<QueueEntry> selectNext(Collection<QueueEntry> candidates);

    /** Returns all current candidates in the same descending order used by {@link #selectNext}. */
    List<QueueEntry> rankCandidates(Collection<QueueEntry> candidates);

    /**
     * Returns the same deterministic ranking together with the score breakdown used to obtain it.
     * Implementations that do not expose a breakdown retain compatibility through this fallback.
     */
    default List<SchedulerCandidateEvaluation> evaluateCandidates(Collection<QueueEntry> candidates) {
        return rankCandidates(candidates).stream()
                .map(entry -> new SchedulerCandidateEvaluation(
                        entry,
                        0,
                        entry.getStatus(),
                        entry.getServiceStage().getStatus(),
                        entry.getServiceRequest().getPriorityClass(),
                        entry.getQueuedAt(),
                        java.time.Duration.ZERO,
                        entry.getServiceRequest().getAppointmentAt(),
                        false,
                        entry.getServiceStage().getPredictedServiceDuration(),
                        entry.getServiceStage().getPredictionSource(),
                        entry.getServiceRequest().getPriorityClass()
                                == com.minor_project.optiserve_backend.operations.domain.PriorityClass.CRITICAL,
                        0, 0, 0, 0, 0,
                        SchedulingFeasibilityStatus.NOT_EVALUATED,
                        null, null,
                        SchedulingCandidateOutcome.NOT_EVALUATED,
                        SchedulingCandidateReason.OTHER_VALIDATED_REASON))
                .toList();
    }
}
