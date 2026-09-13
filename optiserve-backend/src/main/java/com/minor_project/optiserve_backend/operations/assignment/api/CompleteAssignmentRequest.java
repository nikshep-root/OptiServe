package com.minor_project.optiserve_backend.operations.assignment.api;

import jakarta.validation.constraints.Positive;

public record CompleteAssignmentRequest(@Positive Long actualDurationMinutes) {
}
