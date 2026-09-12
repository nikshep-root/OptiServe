package com.minor_project.optiserve_backend.operations.resource.api;

import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResourceResponse(
        UUID id,
        String name,
        ResourceStatus status,
        List<CompatibleServiceTypeResponse> compatibleServiceTypes,
        Instant createdAt,
        Instant updatedAt) {
}
