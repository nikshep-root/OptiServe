package com.minor_project.optiserve_backend.operations.domain;

/** Records how a stage's persisted duration was chosen. */
public enum ServiceDurationPredictionSource {
    ML_MODEL,
    DEFAULT_FALLBACK,
    LEGACY_UNKNOWN
}
