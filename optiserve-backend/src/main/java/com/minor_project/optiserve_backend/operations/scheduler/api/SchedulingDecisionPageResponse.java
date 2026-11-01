package com.minor_project.optiserve_backend.operations.scheduler.api;

import java.util.List;

public record SchedulingDecisionPageResponse(
        List<SchedulingDecisionSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
