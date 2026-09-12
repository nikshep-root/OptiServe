package com.minor_project.optiserve_backend.operations.resource.api;

import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateResourceStatusRequest(
        @NotNull(message = "status is required")
        ResourceStatus status) {
}
