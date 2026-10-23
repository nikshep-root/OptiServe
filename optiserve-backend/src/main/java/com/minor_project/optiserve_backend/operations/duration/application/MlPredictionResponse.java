package com.minor_project.optiserve_backend.operations.duration.application;

import java.math.BigDecimal;

/** Response contract for the FastAPI {@code /predict-duration} endpoint. */
public record MlPredictionResponse(BigDecimal predictedDurationMinutes) {}
