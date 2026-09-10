package com.minor_project.optiserve_backend.operations.servicetype.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateServiceTypeRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must not exceed 150 characters")
        String name,
        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description,
        @NotNull(message = "defaultServiceDurationSeconds is required")
        @Positive(message = "defaultServiceDurationSeconds must be positive")
        Long defaultServiceDurationSeconds,
        @NotNull(message = "active is required")
        Boolean active) {
}
