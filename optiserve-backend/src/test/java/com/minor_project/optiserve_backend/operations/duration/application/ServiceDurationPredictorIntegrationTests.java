package com.minor_project.optiserve_backend.operations.duration.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServiceDurationPredictorIntegrationTests {

    @Autowired private ServiceDurationPredictor serviceDurationPredictor;

    @Test
    void registersTheDefaultPredictorForDependencyInjection() {
        assertThat(serviceDurationPredictor).isInstanceOf(DefaultServiceDurationPredictor.class);
    }
}
