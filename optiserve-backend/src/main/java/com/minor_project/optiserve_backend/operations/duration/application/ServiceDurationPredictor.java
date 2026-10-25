package com.minor_project.optiserve_backend.operations.duration.application;

import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import java.time.Duration;

/** Provides a non-authoritative expected duration for a service stage. */
public interface ServiceDurationPredictor {

    ServiceDurationPrediction predictWithProvenance(ServiceStage serviceStage);

    default Duration predict(ServiceStage serviceStage) {
        return predictWithProvenance(serviceStage).duration();
    }
}
