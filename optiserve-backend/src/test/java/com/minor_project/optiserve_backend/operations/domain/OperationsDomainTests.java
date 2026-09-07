package com.minor_project.optiserve_backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
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
    void resourceExposesAvailabilityAndCompatibilityRules() {
        ServiceType supportedType = serviceType();
        ServiceType unsupportedType = ServiceType.create("Different", null, Duration.ofMinutes(10));
        Resource resource = Resource.create("Counter A", Set.of(supportedType));

        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.AVAILABLE);
        assertThat(resource.supports(supportedType)).isTrue();
        assertThat(resource.supports(unsupportedType)).isFalse();

        resource.markBusy();
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.BUSY);
        assertThatIllegalStateException().isThrownBy(resource::markBusy);

        resource.markOffline();
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.OFFLINE);
    }

    @Test
    void assignmentEnforcesCompatibilityAndSingleActiveAssignment() {
        ServiceType serviceType = serviceType();
        ServiceRequest request = waitingRequest(serviceType);
        Resource compatibleResource = Resource.create("Counter A", Set.of(serviceType));

        Assignment assignment = Assignment.assign(
                request, compatibleResource, REQUESTED_AT, Duration.ofMinutes(15));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.ASSIGNED);
        assertThat(compatibleResource.getStatus()).isEqualTo(ResourceStatus.BUSY);
        assertThatIllegalStateException()
                .isThrownBy(() -> Assignment.assign(request, compatibleResource, REQUESTED_AT, Duration.ofMinutes(15)));

        assignment.start(REQUESTED_AT.plus(Duration.ofMinutes(1)));
        assertThatIllegalStateException().isThrownBy(assignment::cancelBeforeStart);

        assignment.complete(REQUESTED_AT.plus(Duration.ofMinutes(11)));
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(assignment.getActualServiceDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThat(compatibleResource.getStatus()).isEqualTo(ResourceStatus.AVAILABLE);
    }

    @Test
    void incompatibleResourcesCannotReceiveAssignmentsAndOfflineStateIsPreservedOnCompletion() {
        ServiceType requestedType = serviceType();
        ServiceType otherType = ServiceType.create("Different", null, Duration.ofMinutes(10));
        ServiceRequest incompatibleRequest = waitingRequest(requestedType);
        Resource incompatibleResource = Resource.create("Counter A", Set.of(otherType));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Assignment.assign(
                        incompatibleRequest, incompatibleResource, REQUESTED_AT, Duration.ofMinutes(15)));

        ServiceRequest compatibleRequest = waitingRequest(requestedType);
        Resource resource = Resource.create("Counter B", Set.of(requestedType));
        Assignment assignment = Assignment.assign(compatibleRequest, resource, REQUESTED_AT, Duration.ofMinutes(15));
        assignment.start(REQUESTED_AT.plus(Duration.ofMinutes(1)));
        resource.markOffline();

        assignment.complete(REQUESTED_AT.plus(Duration.ofMinutes(11)));

        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.OFFLINE);
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

    private ServiceType serviceType() {
        return ServiceType.create("Standard service", "Default test service", Duration.ofMinutes(15));
    }

    private ServiceRequest waitingRequest(ServiceType serviceType) {
        ServiceRequest request = ServiceRequest.create(serviceType, PriorityClass.NORMAL, null);
        QueueEntry.enter(request, REQUESTED_AT);
        return request;
    }
}
