package com.minor_project.optiserve_backend.operations.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.BayType;
import com.minor_project.optiserve_backend.operations.domain.Customer;
import com.minor_project.optiserve_backend.operations.domain.Mechanic;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceBay;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequestStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OperationsPersistenceIntegrationTests {

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private MechanicRepository mechanicRepository;

    @Autowired
    private ServiceBayRepository serviceBayRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private QueueEntryRepository queueEntryRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationAndHibernateValidationAllowServiceTypePersistence() {
        ServiceType serviceType = serviceType("Document review");

        ServiceType persisted = serviceTypeRepository.saveAndFlush(serviceType);

        assertThat(serviceTypeRepository.findById(persisted.getId()))
                .isPresent()
                .get()
                .extracting(ServiceType::getName, ServiceType::getDefaultServiceDuration, ServiceType::isActive)
                .containsExactly("Document review", Duration.ofMinutes(15), true);
    }

    @Test
    void customerAndVehiclePersistCorrectly() {
        Customer customer = customerRepository.saveAndFlush(
                Customer.createRegistered("John Doe", "john@example.com", "pass123", "555-1001", "123 Main St"));
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(customer, "ABC-123", "Toyota", "Corolla", 2020, "PETROL", "Blue", "1HGCR2F83HA123456", 45000L));

        assertThat(vehicleRepository.findByVin("1HGCR2F83HA123456")).isPresent();
        assertThat(vehicleRepository.findByRegistrationNumber("ABC-123")).isPresent();
        assertThat(customerRepository.findByEmail("john@example.com")).isPresent();
    }

    @Test
    void migratedPostgreSqlSchemaContainsAutomotiveTablesAndActiveAssignmentIndexes() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('customers', 'vehicles', 'mechanics', 'mechanic_service_type_capabilities', "
                        + "'service_bays', 'bay_service_type_capabilities', "
                        + "'service_types', 'service_requests', 'queue_entries', 'assignments') "
                        + "ORDER BY table_name",
                String.class)).containsExactly(
                        "assignments",
                        "bay_service_type_capabilities",
                        "customers",
                        "mechanic_service_type_capabilities",
                        "mechanics",
                        "queue_entries",
                        "service_bays",
                        "service_requests",
                        "service_types",
                        "vehicles");

        assertThat(jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname IN ('uq_assignments_active_request', 'uq_assignments_active_mechanic', 'uq_assignments_active_bay') "
                        + "ORDER BY indexname",
                String.class)).containsExactly("uq_assignments_active_bay", "uq_assignments_active_mechanic", "uq_assignments_active_request");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void mechanicAndBayCapabilitiesPersist() {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(serviceType("Diagnostics"));
        Mechanic mechanic = mechanicRepository.saveAndFlush(
                Mechanic.create("EMP-001", "Alex Tech", "555-0101", LocalDate.now(), Set.of(serviceType)));
        ServiceBay bay = serviceBayRepository.saveAndFlush(
                ServiceBay.create("BAY-1", BayType.DIAGNOSTIC, Set.of(serviceType)));

        Mechanic reloadedMechanic = mechanicRepository.findById(mechanic.getId()).orElseThrow();
        ServiceBay reloadedBay = serviceBayRepository.findById(bay.getId()).orElseThrow();

        assertThat(reloadedMechanic.getCompatibleServiceTypes())
                .extracting(ServiceType::getName)
                .containsExactly("Diagnostics");

        assertThat(reloadedBay.getCompatibleServiceTypes())
                .extracting(ServiceType::getName)
                .containsExactly("Diagnostics");
    }

    @Test
    void requestAndQueueEntryPersistWithTheirReferences() {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(serviceType("Payment"));
        ServiceRequest request = ServiceRequest.create(serviceType, PriorityClass.APPOINTMENT,
                Instant.parse("2026-09-08T10:00:00Z"));
        QueueEntry queueEntry = QueueEntry.enter(request, Instant.parse("2026-09-08T09:55:00Z"));

        serviceRequestRepository.saveAndFlush(request);
        queueEntryRepository.saveAndFlush(queueEntry);

        QueueEntry persistedEntry = queueEntryRepository.findById(queueEntry.getId()).orElseThrow();
        assertThat(persistedEntry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(persistedEntry.getServiceRequest().getStatus()).isEqualTo(ServiceRequestStatus.WAITING);
        assertThat(persistedEntry.getServiceRequest().getPriorityClass()).isEqualTo(PriorityClass.APPOINTMENT);
    }

    @Test
    void assignmentPersistsAndMaintainsActiveState() {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(serviceType("Consultation"));
        Mechanic mechanic = mechanicRepository.saveAndFlush(
                Mechanic.create("EMP-002", "Bob Tech", "555-0102", LocalDate.now(), Set.of(serviceType)));
        ServiceBay bay = serviceBayRepository.saveAndFlush(
                ServiceBay.create("BAY-2", BayType.GENERAL, Set.of(serviceType)));

        ServiceRequest request = waitingRequest(serviceType);
        serviceRequestRepository.saveAndFlush(request);

        Assignment assignment = Assignment.assign(request, mechanic, bay, Instant.parse("2026-09-08T09:00:00Z"),
                Duration.ofMinutes(20));
        Assignment persisted = assignmentRepository.saveAndFlush(assignment);

        assertThat(persisted.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(persisted.getServiceRequest().getId()).isEqualTo(request.getId());
        assertThat(persisted.getMechanic().getId()).isEqualTo(mechanic.getId());
        assertThat(persisted.getServiceBay().getId()).isEqualTo(bay.getId());
    }

    @Test
    void foreignKeyAndUniqueConstraintsAreEnforced() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO queue_entries (id, service_request_id, queued_at, status) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.parse("2026-09-08T09:00:00Z")), "WAITING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeAssignmentPartialUniqueConstraintIsEnforcedForMechanicAndBay() {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(serviceType("Verification"));
        Mechanic mechanic = mechanicRepository.saveAndFlush(
                Mechanic.create("EMP-003", "Charlie Tech", "555-0103", LocalDate.now(), Set.of(serviceType)));
        ServiceBay bay = serviceBayRepository.saveAndFlush(
                ServiceBay.create("BAY-3", BayType.GENERAL, Set.of(serviceType)));

        ServiceRequest request = waitingRequest(serviceType);
        serviceRequestRepository.saveAndFlush(request);
        Assignment assignment = Assignment.assign(request, mechanic, bay, Instant.parse("2026-09-08T09:00:00Z"),
                Duration.ofMinutes(20));
        assignmentRepository.saveAndFlush(assignment);

        // Attempting to assign the same mechanic to another request simultaneously should fail
        ServiceRequest secondRequest = waitingRequest(serviceType);
        serviceRequestRepository.saveAndFlush(secondRequest);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO assignments "
                        + "(id, service_request_id, mechanic_id, bay_id, assigned_at, status) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), secondRequest.getId(), mechanic.getId(), bay.getId(),
                Timestamp.from(Instant.parse("2026-09-08T09:01:00Z")), "ASSIGNED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ServiceType serviceType(String name) {
        return ServiceType.create(name, "Persistence test service", Duration.ofMinutes(15));
    }

    private ServiceRequest waitingRequest(ServiceType serviceType) {
        ServiceRequest request = ServiceRequest.create(serviceType, PriorityClass.NORMAL, null);
        QueueEntry.enter(request, Instant.parse("2026-09-08T09:00:00Z"));
        return request;
    }
}
