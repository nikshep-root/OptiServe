package com.minor_project.optiserve_backend.operations.duration.application;

import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import java.time.Duration;
import java.util.Objects;

/** A validated advisory duration together with its persisted origin. */
public record ServiceDurationPrediction(Duration duration, ServiceDurationPredictionSource source) {

    public ServiceDurationPrediction {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }
}
