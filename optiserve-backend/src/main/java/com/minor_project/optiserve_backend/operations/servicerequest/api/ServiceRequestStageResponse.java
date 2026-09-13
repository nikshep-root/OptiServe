package com.minor_project.optiserve_backend.operations.servicerequest.api;

import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import java.util.UUID;

public record ServiceRequestStageResponse(
        UUID id,
        int sequenceNumber,
        UUID serviceTypeId,
        String serviceTypeName,
        ServiceStageStatus status) {
}
