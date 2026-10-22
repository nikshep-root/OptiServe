package com.minor_project.optiserve_backend.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerProfileRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 32) String phone) {
}
