package com.minor_project.optiserve_backend.operations.resource.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateResourceRequest(
        @Size(min = 1, max = 150, message = "name must be between 1 and 150 characters")
        @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
        String name) {
}
