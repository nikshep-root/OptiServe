package com.minor_project.optiserve_backend.operations.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequestStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
    private ResourceRepository resourceRepository;

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
    void migratedPostgreSqlSchemaContainsOperationsTablesAndActiveAssignmentIndexes() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('service_types', 'resources', 'resource_service_type_capabilities', "
                        + "'service_requests', 'queue_entries', 'assignments') "
                        + "ORDER BY table_name",
                String.class)).containsExactly(
                        "assignments",
                        "queue_entries",
                        "resource_service_type_capabilities",
                        "resources",
                        "service_requests",
                        "service_types");

        assertThat(jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname IN ('uq_assignments_active_request', 'uq_assignments_active_resource') "
                        + "ORDER BY indexname",
                String.class)).containsExactly("uq_assignments_active_request", "uq_assignments_active_resource");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void resourceServiceTypeCompatibilityPersists() {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(serviceType("Registration"));
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Counter A", Set.of(serviceType)));

        Resource reloaded = resourceRepository.findById(resource.getId()).orElseThrow();

        assertThat(reloaded.getCompatibleServiceTypes())
                .extracting(ServiceType::getName)
                .containsExactly("Registration");
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
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Counter B", Set.of(serviceType)));
        ServiceRequest request = waitingRequest(serviceType);
        serviceRequestRepository.saveAndFlush(request);

        Assignment assignment = Assignment.assign(request, resource, Instant.parse("2026-09-08T09:00:00Z"),
                Duration.ofMinutes(20));
        Assignment persisted = assignmentRepository.saveAndFlush(assignment);

        assertThat(persisted.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(persisted.getServiceRequest().getId()).isEqualTo(request.getId());
        assertThat(persisted.getResource().getId()).isEqualTo(resource.getId());
    }

    @Test
    void foreignKeyAndUniqueConstraintsAreEnforced() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO queue_entries (id, service_request_id, queued_at, status) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.parse("2026-09-08T09:00:00Z")), "WAITING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeAssignmentPartialUniqueConstraintIsEnforced() {
        ServiceType serviceType = serviceTypeRepository.saveAndFlush(serviceType("Verification"));
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Counter C", Set.of(serviceType)));
        ServiceRequest request = waitingRequest(serviceType);
        serviceRequestRepository.saveAndFlush(request);
        Assignment assignment = Assignment.assign(request, resource, Instant.parse("2026-09-08T09:00:00Z"),
                Duration.ofMinutes(20));
        assignmentRepository.saveAndFlush(assignment);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO assignments "
                        + "(id, service_request_id, resource_id, assigned_at, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), request.getId(), resource.getId(),
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
