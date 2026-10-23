package com.minor_project.optiserve_backend.operations.duration.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ServiceDurationPredictorIntegrationTests {

    @Test
    void selectsMlPredictorAndKeepsDefaultPredictorAvailableForFallback() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(PredictorConfiguration.class)
                .withPropertyValues(
                        "optiserve.ml.base-url=http://127.0.0.1:1",
                        "optiserve.ml.connect-timeout=50ms",
                        "optiserve.ml.response-timeout=50ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ServiceDurationPredictor.class))
                            .isInstanceOf(MlServiceDurationPredictor.class);
                    assertThat(context.getBean(DefaultServiceDurationPredictor.class)).isNotNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MlServiceDurationPredictor.class, DefaultServiceDurationPredictor.class})
    static class PredictorConfiguration {
    }
}
