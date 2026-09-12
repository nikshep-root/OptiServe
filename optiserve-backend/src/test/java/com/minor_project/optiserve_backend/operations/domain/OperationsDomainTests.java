package com.minor_project.optiserve_backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationsDomainTests {

    private static final Instant TIME = Instant.parse("2026-09-07T09:00:00Z");

    @Test
    void requestLifecycleRejectsInvalidTransitionsAndDoesNotCancelActiveService() {
        ServiceRequest request = ServiceRequest.create(serviceType(), PriorityClass.NORMAL, null);

        assertThatIllegalStateException().isThrownBy(() -> request.complete(Duration.ofMinutes(1)));
        request.enqueue();
        request.assign();
        request.startService();

        assertThatIllegalStateException().isThrownBy(request::cancel);
        request.complete(Duration.ofMinutes(12));
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
    }

    @Test
    void resourceStatusAndCompatibilityRulesRemainEnforced() {
        ServiceType supported = serviceType();
        Resource resource = Resource.create("Counter A", Set.of(supported));

        assertThat(resource.supports(supported)).isTrue();
        assertThat(resource.supports(ServiceType.create("Different", null, Duration.ofMinutes(10)))).isFalse();
        resource.markBusy();
        assertThatIllegalStateException().isThrownBy(resource::markBusy);
        resource.markOffline();
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.OFFLINE);
    }

    @Test
    void firstStageIsEligibleAndLaterStagesRemainPendingInSequenceOrder() {
        ServiceWorkflow workflow = workflow();
        ServiceStage first = workflow.addStage(serviceType(), Duration.ofMinutes(15));
        ServiceStage second = workflow.addStage(serviceType(), Duration.ofMinutes(20));

        assertThat(first.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(first.getEligibleAt()).isNotNull();
        assertThat(second.getStatus()).isEqualTo(ServiceStageStatus.PENDING);
        assertThat(workflow.getStages()).extracting(ServiceStage::getSequenceNumber).containsExactly(1, 2);
    }

    @Test
    void pendingStageCannotBeQueuedOrAssigned() {
        ServiceWorkflow workflow = workflow();
        workflow.addStage(serviceType(), null);
        ServiceStage pending = workflow.addStage(serviceType(), null);

        assertThatIllegalStateException().isThrownBy(pending::queue);
        assertThatIllegalStateException().isThrownBy(pending::assign);
    }

    @Test
    void completingFirstStageMakesOnlyTheNextStageEligible() {
        ServiceWorkflow workflow = workflow();
        ServiceType type = serviceType();
        ServiceStage first = workflow.addStage(type, null);
        ServiceStage second = workflow.addStage(type, null);
        ServiceStage third = workflow.addStage(type, null);
        Resource resource = Resource.create("Counter A", Set.of(type));

        QueueEntry.enter(first, TIME);
        Assignment assignment = Assignment.assign(first, resource, TIME, Duration.ofMinutes(15));
        assignment.start(TIME.plusSeconds(60));
        assignment.complete(TIME.plus(Duration.ofMinutes(16)));

        assertThat(first.getStatus()).isEqualTo(ServiceStageStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(ServiceStageStatus.ELIGIBLE);
        assertThat(third.getStatus()).isEqualTo(ServiceStageStatus.PENDING);
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.ACTIVE);
    }

    @Test
    void invalidTransitionsAndDuplicateActiveAssignmentsAreRejected() {
        ServiceWorkflow workflow = workflow();
        ServiceType type = serviceType();
        ServiceStage stage = workflow.addStage(type, null);
        Resource resource = Resource.create("Counter A", Set.of(type));

        assertThatIllegalStateException().isThrownBy(stage::start);
        QueueEntry.enter(stage, TIME);
        Assignment.assign(stage, resource, TIME, Duration.ofMinutes(15));

        assertThatIllegalStateException()
                .isThrownBy(() -> Assignment.assign(stage, resource, TIME, Duration.ofMinutes(15)));
        assertThatIllegalStateException().isThrownBy(() -> workflow.completeStage(stage, TIME));
    }

    @Test
    void assignmentUsesStageCapabilityAndCompletesWorkflow() {
        ServiceWorkflow workflow = workflow();
        ServiceType type = serviceType();
        ServiceStage stage = workflow.addStage(type, null);
        Resource resource = Resource.create("Counter A", Set.of(type));

        QueueEntry.enter(stage, TIME);
        Assignment assignment = Assignment.assign(stage, resource, TIME, Duration.ofMinutes(15));
        assignment.start(TIME.plusSeconds(60));
        resource.markOffline();
        assignment.complete(TIME.plus(Duration.ofMinutes(11)));

        assertThat(assignment.getActualServiceDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.OFFLINE);
        assertThat(workflow.getStatus()).isEqualTo(ServiceWorkflowStatus.COMPLETED);
    }

    @Test
    void incompatibleResourceCannotReceiveStageAssignmentAndQueueEntryCanBeRemoved() {
        ServiceWorkflow workflow = workflow();
        ServiceType required = serviceType();
        ServiceStage stage = workflow.addStage(required, null);
        QueueEntry entry = QueueEntry.enter(stage, TIME);
        Resource incompatible = Resource.create("Counter A", Set.of(ServiceType.create(
                "Different", null, Duration.ofMinutes(10))));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Assignment.assign(stage, incompatible, TIME, Duration.ofMinutes(15)));
        entry.remove();
        assertThatIllegalStateException().isThrownBy(entry::remove);
    }

    private ServiceWorkflow workflow() {
        return ServiceWorkflow.create(ServiceRequest.create(serviceType(), PriorityClass.NORMAL, null));
    }

    private ServiceType serviceType() {
        return ServiceType.create("Standard service", "Test service", Duration.ofMinutes(15));
    }
}
