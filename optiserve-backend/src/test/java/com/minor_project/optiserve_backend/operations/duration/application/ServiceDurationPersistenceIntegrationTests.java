package com.minor_project.optiserve_backend.operations.duration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ServiceDurationPersistenceIntegrationTests {

    private static final HttpServer ML_STUB = startMlStub();
    private static final AtomicInteger ML_STATUS = new AtomicInteger(200);

    @Autowired private ServiceRequestApplicationService serviceRequestApplicationService;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceWorkflowRepository serviceWorkflowRepository;
    @Autowired private ServiceRequestRepository serviceRequestRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthUserRepository users;
    @Autowired private CustomerRepository customers;

    @DynamicPropertySource
    static void mlServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("optiserve.ml.base-url", () -> "http://127.0.0.1:" + ML_STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopMlStub() {
        ML_STUB.stop(0);
    }

    @BeforeEach
    void resetMlResponse() {
        ML_STATUS.set(200);
    }

    @Test
    void persistsMlDurationAndProvenanceAndSerializesThemAfterReload() throws Exception {
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(CustomerTestFixtures.createPersistedCustomer(users, customers),
                        "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Corolla", 2020));
        ServiceType oilChange = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Oil Change", null, Duration.ofMinutes(35)));
        ServiceType inspection = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Inspection", null, Duration.ofMinutes(20)));

        var created = serviceRequestApplicationService.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(oilChange.getId(), inspection.getId())));

        ServiceWorkflow persistedWorkflow = serviceWorkflowRepository.findByServiceRequestId(created.id()).orElseThrow();
        assertThat(persistedWorkflow.getStages())
                .extracting(stage -> stage.getServiceType().getName(), stage -> stage.getPredictedServiceDuration())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Oil Change", Duration.ofMinutes(74)),
                        org.assertj.core.groups.Tuple.tuple("Inspection", Duration.ofMinutes(74)));
        assertThat(persistedWorkflow.getStages()).extracting(stage -> stage.getPredictionSource())
                .containsExactly(ServiceDurationPredictionSource.ML_MODEL, ServiceDurationPredictionSource.ML_MODEL);

        entityManager.flush();
        entityManager.clear();
        var reloadedResponse = serviceRequestApplicationService.findById(created.id());
        var json = objectMapper.readTree(objectMapper.writeValueAsString(reloadedResponse));
        assertThat(json.at("/stages/0/predictedServiceDuration").asString()).isEqualTo("PT1H14M");
        assertThat(json.at("/stages/0/predictionSource").asString()).isEqualTo("ML_MODEL");
    }

    @Test
    void persistsDefaultFallbackProvenanceWhenMlServiceReturnsAnError() {
        ML_STATUS.set(503);
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(CustomerTestFixtures.createPersistedCustomer(users, customers),
                        "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Corolla", 2020));
        ServiceType oilChange = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Fallback Oil Change", null, Duration.ofMinutes(35)));

        var created = serviceRequestApplicationService.create(new CreateServiceRequestRequest(
                vehicle.getId(), PriorityClass.NORMAL, null, List.of(oilChange.getId())));

        entityManager.flush();
        entityManager.clear();
        ServiceWorkflow persisted = serviceWorkflowRepository.findByServiceRequestId(created.id()).orElseThrow();
        assertThat(persisted.getStages().getFirst().getPredictedServiceDuration()).isEqualTo(Duration.ofMinutes(35));
        assertThat(persisted.getStages().getFirst().getPredictionSource())
                .isEqualTo(ServiceDurationPredictionSource.DEFAULT_FALLBACK);
    }

    @Test
    void assignsLegacyUnknownToStagesWithoutRecordedProvenance() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(CustomerTestFixtures.createPersistedCustomer(users, customers),
                        "KA" + UUID.randomUUID().toString().substring(0, 8), "Toyota", "Corolla", 2020));
        ServiceType type = serviceTypeRepository.saveAndFlush(
                ServiceType.create("Historical Service", null, Duration.ofMinutes(25)));
        ServiceRequest request = serviceRequestRepository.saveAndFlush(
                ServiceRequest.create(vehicle, type, PriorityClass.NORMAL, null));
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        workflow.addStage(type, null);
        serviceWorkflowRepository.saveAndFlush(workflow);
        entityManager.clear();

        ServiceWorkflow reloaded = serviceWorkflowRepository.findByServiceRequestId(request.getId()).orElseThrow();
        assertThat(reloaded.getStages().getFirst().getPredictionSource())
                .isEqualTo(ServiceDurationPredictionSource.LEGACY_UNKNOWN);
    }

    private static HttpServer startMlStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/predict-duration", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] body = (ML_STATUS.get() == 200
                        ? "{\"predictedDurationMinutes\":74}"
                        : "{\"detail\":\"prediction unavailable\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(ML_STATUS.get(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
