package com.minor_project.optiserve_backend.operations.servicetype.api;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateServiceTypeRequest(
        @Size(min = 1, max = 150, message = "name must be between 1 and 150 characters")
        @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
        String name,
        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description,
        @Positive(message = "defaultServiceDurationSeconds must be positive")
        Long defaultServiceDurationSeconds,
        Boolean active) {
}
