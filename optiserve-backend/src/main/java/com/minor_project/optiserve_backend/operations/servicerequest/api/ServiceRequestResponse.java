package com.minor_project.optiserve_backend.operations.servicerequest.api;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequestStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceRequestResponse(
        UUID id,
        UUID vehicleId,
        String vehicleRegistrationNumber,
        PriorityClass priority,
        Instant appointmentTime,
        ServiceRequestStatus status,
        UUID workflowId,
        ServiceWorkflowStatus workflowStatus,
        List<ServiceRequestStageResponse> stages) {
}
