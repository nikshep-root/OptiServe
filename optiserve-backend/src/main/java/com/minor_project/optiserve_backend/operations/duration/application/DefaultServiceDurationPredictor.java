package com.minor_project.optiserve_backend.operations.duration.application;

import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Temporary deterministic predictor that uses the configured ServiceType duration.
 * A future ML-backed implementation can replace this bean without changing callers.
 */
@Service
public class DefaultServiceDurationPredictor implements ServiceDurationPredictor {

    @Override
    public Duration predict(ServiceStage serviceStage) {
        Objects.requireNonNull(serviceStage, "serviceStage must not be null");
        return serviceStage.getServiceType().getDefaultServiceDuration();
    }
}
