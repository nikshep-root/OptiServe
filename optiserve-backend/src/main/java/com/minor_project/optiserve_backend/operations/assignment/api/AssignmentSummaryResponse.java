package com.minor_project.optiserve_backend.operations.assignment.api;

import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import java.time.Instant;
import java.util.UUID;

public record AssignmentSummaryResponse(
        UUID assignmentId,
        UUID serviceRequestId,
        UUID stageId,
        String vehicleRegistrationNumber,
        String serviceTypeName,
        PriorityClass priority,
        String resourceName,
        AssignmentStatus assignmentStatus,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt) {
}
