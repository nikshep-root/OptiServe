package com.minor_project.optiserve_backend.operations.scheduler.api;

import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionReason;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import java.time.Instant;
import java.util.UUID;

public record SchedulingDecisionSummaryResponse(
        UUID id,
        Instant evaluatedAt,
        SchedulingTrigger trigger,
        String strategyName,
        String strategyVersion,
        SchedulingDecisionResult result,
        SchedulingDecisionReason reason,
        UUID selectedStageId,
        UUID selectedResourceId,
        int candidateCount,
        int evaluatedCandidateCount) {
}
