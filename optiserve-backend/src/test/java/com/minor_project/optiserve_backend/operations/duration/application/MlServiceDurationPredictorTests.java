package com.minor_project.optiserve_backend.operations.duration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile long delayMillis;
    private MlServiceDurationPredictor predictor;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void startStubServer() throws IOException {
        responseStatus = 200;
        responseBody = readContractFixture("response.json");
        delayMillis = 0;
        capturedRequestBody = "";
        requests.set(0);
        meterRegistry = new SimpleMeterRegistry();
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
    void postsSharedFastApiContractAndParsesSharedResponseFixture() throws Exception {
        ServiceStage stage = stage(2020, Instant.parse("2026-10-23T06:00:00Z"));

        assertThat(predictor.predict(stage)).isEqualTo(Duration.ofMinutes(87));
        assertThat(metric(MlServiceDurationPredictor.SUCCESS_METRIC)).isEqualTo(1);
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isZero();
        assertThat(requests).hasValue(1);
        assertThat(OBJECT_MAPPER.readTree(lastRequestBody()))
                .isEqualTo(OBJECT_MAPPER.readTree(readContractFixture("request.json")));
    }

    @Test
    void marksOnlyAValidMlResponseAsModelProvenance() {
        assertThat(predictor.predictWithProvenance(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))).source())
                .isEqualTo(ServiceDurationPredictionSource.ML_MODEL);
    }

    @Test
    void marksInvalidMlResponseAsDefaultFallback() {
        responseBody = "{\"predictedDurationMinutes\":0}";

        assertThat(predictor.predictWithProvenance(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))).source())
                .isEqualTo(ServiceDurationPredictionSource.DEFAULT_FALLBACK);
    }

    @Test
    void marksUnsupportedVehicleYearAsDefaultFallback() {
        assertThat(predictor.predictWithProvenance(stage(2007, Instant.parse("2026-10-23T06:00:00Z"))).source())
                .isEqualTo(ServiceDurationPredictionSource.DEFAULT_FALLBACK);
        assertThat(requests).hasValue(0);
    }

    @Test
    void usesAppointmentDateForTheModelWeekdayInConfiguredServiceTimeZone() throws Exception {
        ServiceStage stage = stage(2020, Instant.parse("2026-10-23T06:00:00Z"));

        predictor.predict(stage);

        JsonNode request = OBJECT_MAPPER.readTree(lastRequestBody());
        assertThat(request.get("serviceType").textValue()).isEqualTo("Oil Change");
        assertThat(request.get("vehicleMake").textValue()).isEqualTo("Toyota");
        assertThat(request.get("vehicleModel").textValue()).isEqualTo("Corolla");
        assertThat(request.get("vehicleYear").intValue()).isEqualTo(2020);
        assertThat(request.get("dayOfWeek").textValue()).isEqualTo("FRIDAY");
        assertThat(request.has("vehicleAge")).isFalse();
        assertThat(request.has("priority")).isFalse();
        assertThat(request.has("waitingTime")).isFalse();
    }

    @Test
    void fallsBackWhenMlServiceReturnsAnError() {
        responseStatus = 503;
        responseBody = "{\"detail\":\"model unavailable\"}";

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
        assertThat(metric(MlServiceDurationPredictor.ERROR_METRIC)).isEqualTo(1);
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isEqualTo(1);
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
        assertThat(metric(MlServiceDurationPredictor.ERROR_METRIC)).isEqualTo(3);
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 4, 1441})
    void fallsBackForNonPositiveOrUnreasonablePredictions(int minutes) {
        responseBody = "{\"predictedDurationMinutes\":" + minutes + "}";

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
        assertThat(metric(MlServiceDurationPredictor.ERROR_METRIC)).isEqualTo(1);
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 1440})
    void acceptsConfiguredPredictionBoundaryValues(int minutes) {
        responseBody = "{\"predictedDurationMinutes\":" + minutes + "}";

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(minutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {2008, 2026})
    void sendsFastApiVehicleYearBoundaryValues(int year) throws Exception {
        predictor.predict(stage(year, Instant.parse("2026-10-23T06:00:00Z")));

        JsonNode body = OBJECT_MAPPER.readTree(lastRequestBody());
        assertThat(body.get("vehicleYear").intValue()).isEqualTo(year);
        assertThat(requests).hasValue(1);
    }

    @Test
    void fallsBackWhenMlResponseTimesOut() {
        delayMillis = 250;
        predictor = newPredictor(server.getAddress().getPort(), Duration.ofMillis(30));

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
        assertThat(metric(MlServiceDurationPredictor.TIMEOUT_METRIC)).isEqualTo(1);
        assertThat(metric(MlServiceDurationPredictor.ERROR_METRIC)).isZero();
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isEqualTo(1);
    }

    @Test
    void fallsBackWhenMlServiceIsUnavailable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;
        predictor = newPredictor(port, Duration.ofMillis(30));

        assertThat(predictor.predict(stage(2020, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
        assertThat(metric(MlServiceDurationPredictor.ERROR_METRIC)).isEqualTo(1);
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isEqualTo(1);
    }

    @Test
    void doesNotSendOutOfContractVehicleYearAndUsesDefault() {
        assertThat(predictor.predict(stage(2007, Instant.parse("2026-10-23T06:00:00Z"))))
                .isEqualTo(Duration.ofMinutes(35));
        assertThat(requests).hasValue(0);
        assertThat(metric(MlServiceDurationPredictor.FALLBACK_METRIC)).isEqualTo(1);
        assertThat(metric(MlServiceDurationPredictor.ERROR_METRIC)).isZero();
    }

    private String lastRequestBody() {
        return capturedRequestBody;
    }

    private static String readContractFixture(String fileName) throws IOException {
        String resource = "contracts/ml-duration-prediction/" + fileName;
        try (InputStream input = MlServiceDurationPredictorTests.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing shared ML contract fixture: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private volatile String capturedRequestBody = "";

    private MlServiceDurationPredictor newPredictor(int port, Duration responseTimeout) {
        return new MlServiceDurationPredictor(
                new DefaultServiceDurationPredictor(),
                meterRegistry,
                "http://127.0.0.1:" + port,
                Duration.ofMillis(200),
                responseTimeout,
                "Asia/Kolkata",
                5,
                1440);
    }

    private double metric(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private ServiceStage stage(int vehicleYear, Instant appointmentAt) {
        Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Corolla", vehicleYear);
        ServiceType serviceType = ServiceType.create("Oil Change", null, Duration.ofMinutes(35));
        ServiceRequest request = ServiceRequest.create(vehicle, serviceType, PriorityClass.APPOINTMENT, appointmentAt);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        return workflow.addStage(serviceType, null);
    }
}
