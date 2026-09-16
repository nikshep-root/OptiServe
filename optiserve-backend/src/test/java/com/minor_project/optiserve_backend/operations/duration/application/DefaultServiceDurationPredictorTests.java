package com.minor_project.optiserve_backend.operations.duration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultServiceDurationPredictorTests {

    private final DefaultServiceDurationPredictor predictor = new DefaultServiceDurationPredictor();

    @Test
    void returnsTheServiceTypeDefaultDuration() {
        ServiceStage stage = stageWithDefaultDuration(Duration.ofMinutes(45));

        assertThat(predictor.predict(stage)).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void returnsAPositiveDurationForAValidServiceType() {
        ServiceStage stage = stageWithDefaultDuration(Duration.ofSeconds(30));

        Duration predictedDuration = predictor.predict(stage);

        assertThat(predictedDuration.isNegative()).isFalse();
        assertThat(predictedDuration.isZero()).isFalse();
    }

    @Test
    void doesNotMutateTheStageOrServiceType() {
        ServiceStage stage = stageWithDefaultDuration(Duration.ofMinutes(20));
        ServiceType serviceType = stage.getServiceType();
        Duration originalDuration = serviceType.getDefaultServiceDuration();
        var originalStageStatus = stage.getStatus();

        predictor.predict(stage);

        assertThat(serviceType.getDefaultServiceDuration()).isEqualTo(originalDuration);
        assertThat(stage.getStatus()).isEqualTo(originalStageStatus);
    }

    private static ServiceStage stageWithDefaultDuration(Duration defaultDuration) {
        ServiceType serviceType = ServiceType.create("Service " + UUID.randomUUID(), null, defaultDuration);
        Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024);
        ServiceRequest request = ServiceRequest.create(vehicle, serviceType, PriorityClass.NORMAL, null);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        return workflow.addStage(serviceType, null);
    }
}
