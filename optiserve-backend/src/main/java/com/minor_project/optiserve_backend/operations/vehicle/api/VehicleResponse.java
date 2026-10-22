package com.minor_project.optiserve_backend.operations.vehicle.api;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id, String registrationNumber, String make, String model, Integer year,
        Instant createdAt, Instant updatedAt) {
}
