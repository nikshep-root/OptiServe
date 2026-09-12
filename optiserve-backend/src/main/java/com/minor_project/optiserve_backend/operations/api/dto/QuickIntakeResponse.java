package com.minor_project.optiserve_backend.operations.api.dto;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record QuickIntakeResponse(
        UUID serviceRequestId,
        UUID queueEntryId,
        UUID vehicleId,
        UUID customerId,
        String registrationNumber,
        String customerName,
        String serviceTypeName,
        PriorityClass priorityClass,
        ServiceRequestStatus requestStatus,
        QueueEntryStatus queueStatus,
        Instant queuedAt
) {}
