package com.minor_project.optiserve_backend.operations.scheduler.api;

import java.util.List;

public record SchedulingDecisionDetailResponse(
        SchedulingDecisionSummaryResponse summary,
        List<SchedulingCandidateEvaluationResponse> candidates) {
}
