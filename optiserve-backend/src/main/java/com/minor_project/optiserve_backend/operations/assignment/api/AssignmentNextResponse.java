package com.minor_project.optiserve_backend.operations.assignment.api;

import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import java.util.UUID;

public record AssignmentNextResponse(
        AssignmentNextResult result,
        UUID assignmentId,
        UUID serviceRequestId,
        UUID vehicleId,
        String vehicleRegistrationNumber,
        UUID workflowId,
        UUID stageId,
        UUID serviceTypeId,
        String serviceTypeName,
        PriorityClass priority,
        UUID resourceId,
        String resourceName,
        AssignmentStatus assignmentStatus) {
}
