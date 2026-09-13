package com.minor_project.optiserve_backend.operations.queue.api;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import java.time.Instant;
import java.util.UUID;

public record QueueEntryResponse(
        UUID id,
        UUID stageId,
        UUID workflowId,
        UUID serviceRequestId,
        UUID vehicleId,
        String vehicleRegistrationNumber,
        UUID serviceTypeId,
        String serviceTypeName,
        PriorityClass priority,
        Instant queueEntryTime,
        QueueEntryStatus status,
        ServiceStageStatus stageStatus) {
}
