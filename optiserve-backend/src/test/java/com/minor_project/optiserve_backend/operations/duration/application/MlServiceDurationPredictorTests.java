package com.minor_project.optiserve_backend.operations.duration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MlServiceDurationPredictorTests {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile long delayMillis;
    private MlServiceDurationPredictor predictor;

    @BeforeEach
    void startStubServer() throws IOException {
        responseStatus = 200;
        responseBody = "{\"predictedDurationMinutes\":87}";
        delayMillis = 0;
        capturedRequestBody = "";
        requests.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict-duration", exchange -> {
            requests.incrementAndGet();
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            exchange.getRequestBody().close();
            capturedRequestBody = new String(requestBody, StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        predictor = newPredictor(server.getAddress().getPort(), Duration.ofMillis(100));
    }

    @AfterEach
    void stopStubServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsFastApiContractAndParsesPredictionAsDuration() {
        ServiceStage stage = stage(2020, Instant.parse("2026-10-23T06:00:00Z"));

        assertThat(predictor.predict(stage)).isEqualTo(Duration.ofMinutes(87));
        assertThat(requests).hasValue(1);
    }

    @Test
    void usesAppointmentDateForTheModelWeekdayInConfiguredServiceTimeZone() {
        ServiceStage stage = stage(2020, Instant.parse("2026-10-23T06:00:00Z"));

        predictor.predict(stage);

        assertThat(lastRequestBody()).contains("\"serviceType\":\"Oil Change\"")
                .contains("\"vehicleMake\":\"Toyota\"")
                .contains("\"vehicleModel\":\"Corolla\"")
                .contains("\"vehicleYear\":2020")
                .contains("\"dayOfWeek\":\"FRIDAY\"")
                .doesNotContain("vehicleAge", "priority", "waitingTime");
    }

    @Test
    void fallsBackWhenMlServiceReturnsAnError() {
        responseStatus = 503;
        responseBody = "{\"detail\":\"model unavailable\"}";

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void fallsBackWhenMlServiceReturnsMalformedOrIncompleteJson() {
        responseBody = "not-json";
        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));

        responseBody = "{}";
        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));

        responseBody = "{\"predictedDurationMinutes\":87.5}";
        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1441})
    void fallsBackForNonPositiveOrUnreasonablePredictions(int minutes) {
        responseBody = "{\"predictedDurationMinutes\":" + minutes + "}";

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void fallsBackWhenMlResponseTimesOut() {
        delayMillis = 250;
        predictor = newPredictor(server.getAddress().getPort(), Duration.ofMillis(30));

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void fallsBackWhenMlServiceIsUnavailable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;
        predictor = newPredictor(port, Duration.ofMillis(30));

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void doesNotSendOutOfContractVehicleYearAndUsesDefault() {
        assertThat(predictor.predict(stage(2007, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
        assertThat(requests).hasValue(0);
    }

    private String lastRequestBody() {
        return capturedRequestBody;
    }

    private volatile String capturedRequestBody = "";

    private MlServiceDurationPredictor newPredictor(int port, Duration responseTimeout) {
        return new MlServiceDurationPredictor(
                new DefaultServiceDurationPredictor(),
                "http://127.0.0.1:" + port,
                Duration.ofMillis(200),
                responseTimeout,
                "Asia/Kolkata",
                5,
                1440);
    }

    private ServiceStage stage(int vehicleYear, Instant appointmentAt) {
        Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Corolla", vehicleYear);
        ServiceType serviceType = ServiceType.create("Oil Change", null, Duration.ofMinutes(35));
        ServiceRequest request = ServiceRequest.create(vehicle, serviceType, PriorityClass.APPOINTMENT, appointmentAt);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        return workflow.addStage(serviceType, null);
    }
}
