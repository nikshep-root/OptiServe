package com.minor_project.optiserve_backend.operations.servicetype.api;

import java.time.Instant;
import java.util.UUID;

public record ServiceTypeResponse(
        UUID id,
        String name,
        String description,
        long defaultServiceDurationSeconds,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
