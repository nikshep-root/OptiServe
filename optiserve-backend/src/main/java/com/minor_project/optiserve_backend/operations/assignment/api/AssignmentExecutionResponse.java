package com.minor_project.optiserve_backend.operations.assignment.api;

import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record AssignmentExecutionResponse(UUID assignmentId, AssignmentStatus assignmentStatus,
        ServiceStageStatus stageStatus, ResourceStatus resourceStatus, Instant startedAt,
        Instant completedAt, Duration actualServiceDuration) {
}
