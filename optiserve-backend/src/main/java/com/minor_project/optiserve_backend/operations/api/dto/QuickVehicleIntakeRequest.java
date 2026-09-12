package com.minor_project.optiserve_backend.operations.api.dto;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record QuickVehicleIntakeRequest(
        @NotBlank(message = "Registration / license plate number is required")
        String registrationNumber,

        String vin,

        @NotBlank(message = "Make is required")
        String make,

        @NotBlank(message = "Model is required")
        String model,

        Integer manufacturingYear,

        String fuelType,

        String color,

        Long currentMileage,

        @NotBlank(message = "Customer name is required")
        String customerName,

        @NotBlank(message = "Customer phone is required")
        String customerPhone,

        String customerEmail,

        @NotNull(message = "Service type id is required")
        UUID serviceTypeId,

        PriorityClass priorityClass
) {
    public PriorityClass resolvePriority() {
        return priorityClass != null ? priorityClass : PriorityClass.NORMAL;
    }
}
