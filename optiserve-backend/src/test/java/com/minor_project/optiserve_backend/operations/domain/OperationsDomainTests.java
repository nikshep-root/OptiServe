package com.minor_project.optiserve_backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationsDomainTests {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-07T09:00:00Z");

    @Test
    void appointmentRequestsRequireAppointmentTimeAndPriorityClassesRemainExplicit() {
        ServiceType serviceType = serviceType();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ServiceRequest.create(serviceType, PriorityClass.APPOINTMENT, null));

        ServiceRequest request = ServiceRequest.create(
                serviceType, PriorityClass.APPOINTMENT, REQUESTED_AT.plus(Duration.ofHours(1)));

        assertThat(request.getPriorityClass()).isEqualTo(PriorityClass.APPOINTMENT);
        assertThat(PriorityClass.values()).containsExactly(
                PriorityClass.CRITICAL,
                PriorityClass.URGENT,
                PriorityClass.APPOINTMENT,
                PriorityClass.NORMAL);
    }

    @Test
    void requestLifecycleRejectsInvalidTransitionsAndDoesNotCancelActiveService() {
        ServiceRequest request = ServiceRequest.create(serviceType(), PriorityClass.NORMAL, null);

        assertThatIllegalStateException().isThrownBy(() -> request.complete(Duration.ofMinutes(1)));

        QueueEntry.enter(request, REQUESTED_AT);
        request.assign();
        request.startService();

        assertThatIllegalStateException().isThrownBy(request::cancel);

        request.complete(Duration.ofMinutes(12));
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThatIllegalStateException().isThrownBy(request::enqueue);
    }

    @Test
    void mechanicExposesAvailabilityAndCapabilityRules() {
        ServiceType supportedType = serviceType();
        ServiceType unsupportedType = ServiceType.create("Different", null, Duration.ofMinutes(10));
        Mechanic mechanic = Mechanic.create("EMP-001", "John Tech", "555-0100", LocalDate.now(), Set.of(supportedType));

        assertThat(mechanic.getStatus()).isEqualTo(MechanicStatus.AVAILABLE);
        assertThat(mechanic.supports(supportedType)).isTrue();
        assertThat(mechanic.supports(unsupportedType)).isFalse();

        mechanic.markBusy();
        assertThat(mechanic.getStatus()).isEqualTo(MechanicStatus.BUSY);
        assertThatIllegalStateException().isThrownBy(mechanic::markBusy);

        mechanic.markOffline();
        assertThat(mechanic.getStatus()).isEqualTo(MechanicStatus.OFFLINE);
    }

    @Test
    void serviceBayExposesAvailabilityAndCapabilityRules() {
        ServiceType supportedType = serviceType();
        ServiceType unsupportedType = ServiceType.create("Different", null, Duration.ofMinutes(10));
        ServiceBay bay = ServiceBay.create("BAY-1", BayType.GENERAL, Set.of(supportedType));

        assertThat(bay.getStatus()).isEqualTo(BayStatus.AVAILABLE);
        assertThat(bay.supports(supportedType)).isTrue();
        assertThat(bay.supports(unsupportedType)).isFalse();

        bay.markOccupied();
        assertThat(bay.getStatus()).isEqualTo(BayStatus.OCCUPIED);
        assertThatIllegalStateException().isThrownBy(bay::markOccupied);

        bay.markOffline();
        assertThat(bay.getStatus()).isEqualTo(BayStatus.OFFLINE);
    }

    @Test
    void assignmentEnforcesDualCompatibilityAndSingleActiveAssignment() {
        ServiceType serviceType = serviceType();
        ServiceRequest request = waitingRequest(serviceType);
        Mechanic mechanic = Mechanic.create("EMP-001", "John Tech", "555-0100", LocalDate.now(), Set.of(serviceType));
        ServiceBay bay = ServiceBay.create("BAY-1", BayType.GENERAL, Set.of(serviceType));

        Assignment assignment = Assignment.assign(
                request, mechanic, bay, REQUESTED_AT, Duration.ofMinutes(15));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.ASSIGNED);
        assertThat(mechanic.getStatus()).isEqualTo(MechanicStatus.BUSY);
        assertThat(bay.getStatus()).isEqualTo(BayStatus.OCCUPIED);

        assertThatIllegalStateException()
                .isThrownBy(() -> Assignment.assign(request, mechanic, bay, REQUESTED_AT, Duration.ofMinutes(15)));

        assignment.start(REQUESTED_AT.plus(Duration.ofMinutes(1)));
        assertThatIllegalStateException().isThrownBy(assignment::cancelBeforeStart);

        assignment.complete(REQUESTED_AT.plus(Duration.ofMinutes(11)));
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getActualServiceDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThat(mechanic.getStatus()).isEqualTo(MechanicStatus.AVAILABLE);
        assertThat(bay.getStatus()).isEqualTo(BayStatus.AVAILABLE);
    }

    @Test
    void incompatibleMechanicOrBayCannotReceiveAssignments() {
        ServiceType requestedType = serviceType();
        ServiceType otherType = ServiceType.create("Different", null, Duration.ofMinutes(10));
        ServiceRequest request = waitingRequest(requestedType);

        Mechanic incompatibleMechanic = Mechanic.create("EMP-001", "John Tech", "555-0100", LocalDate.now(), Set.of(otherType));
        ServiceBay compatibleBay = ServiceBay.create("BAY-1", BayType.GENERAL, Set.of(requestedType));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Assignment.assign(
                        request, incompatibleMechanic, compatibleBay, REQUESTED_AT, Duration.ofMinutes(15)));

        Mechanic compatibleMechanic = Mechanic.create("EMP-002", "Jane Tech", "555-0200", LocalDate.now(), Set.of(requestedType));
        ServiceBay incompatibleBay = ServiceBay.create("BAY-2", BayType.EV, Set.of(otherType));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Assignment.assign(
                        request, compatibleMechanic, incompatibleBay, REQUESTED_AT, Duration.ofMinutes(15)));
    }

    @Test
    void queueEntryTracksQueueParticipationWithoutStoringWaitingTime() {
        ServiceRequest request = ServiceRequest.create(serviceType(), PriorityClass.CRITICAL, null);

        QueueEntry queueEntry = QueueEntry.enter(request, REQUESTED_AT);

        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(queueEntry.getServiceRequest()).isSameAs(request);
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.WAITING);

        queueEntry.remove();
        assertThat(queueEntry.getStatus()).isEqualTo(QueueEntryStatus.REMOVED);
        assertThatIllegalStateException().isThrownBy(queueEntry::remove);
    }

    @Test
    void customerAndVehicleSupportEmergencyAndRegisteredScenarios() {
        Customer guest = Customer.createGuest("Emergency Services", "911-0000");
        assertThat(guest.getName()).isEqualTo("Emergency Services");
        assertThat(guest.getEmail()).isNull();
        assertThat(guest.getPassword()).isNull();

        Customer registered = Customer.createRegistered("Alice Smith", "alice@example.com", "secret123", "555-1234", "123 Elm St");
        assertThat(registered.getEmail()).isEqualTo("alice@example.com");

        Vehicle ambulance = Vehicle.create(
                guest, "AMB-911", "Ford", "Transit Ambulance", 2023, "DIESEL", "WHITE", "1FTNE3Y89PK123456", 15000L);
        assertThat(ambulance.getRegistrationNumber()).isEqualTo("AMB-911");
        assertThat(ambulance.getCurrentMileage()).isEqualTo(15000L);
        assertThat(ambulance.getCustomer()).isSameAs(guest);
    }

    private ServiceType serviceType() {
        return ServiceType.create("Standard service", "Default test service", Duration.ofMinutes(15));
    }

    private ServiceRequest waitingRequest(ServiceType serviceType) {
        ServiceRequest request = ServiceRequest.create(serviceType, PriorityClass.NORMAL, null);
        QueueEntry.enter(request, REQUESTED_AT);
        return request;
    }
}
