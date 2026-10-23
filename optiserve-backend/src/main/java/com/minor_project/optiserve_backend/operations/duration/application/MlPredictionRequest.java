package com.minor_project.optiserve_backend.operations.duration.application;

/** Request contract for the FastAPI {@code /predict-duration} endpoint. */
public record MlPredictionRequest(
        String serviceType,
        String vehicleMake,
        String vehicleModel,
        int vehicleYear,
        String dayOfWeek) {}
