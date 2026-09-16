package com.minor_project.optiserve_backend.operations.waittime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import com.minor_project.optiserve_backend.operations.scheduler.domain.HybridSchedulerStrategy;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitTimeCalculationServiceTests {
    @Mock QueueEntryRepository queues; @Mock ResourceRepository resources; @Mock AssignmentRepository assignments; @Mock SchedulerStrategy scheduler;

    @Test
    void ignoresQueuedWorkIncompatibleWithEveryTargetResourceLane() throws Exception {
        ServiceType a=ServiceType.create("A",null,Duration.ofMinutes(10)), b=ServiceType.create("B",null,Duration.ofMinutes(30));
        set(a,"id",UUID.randomUUID()); set(b,"id",UUID.randomUUID());
        QueueEntry target=entry(a), incompatible=entry(b); UUID stageId=target.getServiceStage().getId();
        Resource lane=Resource.create("A lane", Set.of(a));
        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE,a.getId())).thenReturn(List.of(lane));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY,a.getId())).thenReturn(List.of());
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS)).thenReturn(List.of());
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING)).thenReturn(new java.util.ArrayList<>(List.of(target,incompatible)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(incompatible),Optional.of(target));
        assertThat(service().estimateMinutes(stageId)).isZero();
    }

    @Test
    void returnsZeroWhenTargetHasAnAvailableCompatibleResourceAndNoWorkAhead() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(10));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        UUID stageId = target.getServiceStage().getId();
        Resource availableResource = Resource.create("A lane", Set.of(serviceType));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of(availableResource));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of());
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(target));

        assertThat(service().estimateMinutes(stageId)).isZero();
    }

    @Test
    void includesTwentyMinutesRemainingForAnInProgressAssignmentOnTheOnlyCompatibleResource() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(60));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        QueueEntry activeWork = entry(serviceType);
        UUID stageId = target.getServiceStage().getId();
        Resource busyResource = Resource.create("A lane", Set.of(serviceType));
        set(busyResource, "id", UUID.randomUUID());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Assignment activeAssignment = Assignment.assign(
                activeWork.getServiceStage(), busyResource, now.minus(Duration.ofMinutes(40)), null);
        activeAssignment.start(now.minus(Duration.ofMinutes(40)));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of());
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of(busyResource));
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of(activeAssignment));
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(target));

        assertThat(service().estimateMinutes(stageId)).isEqualTo(20);
    }

    @Test
    void usesTheAvailableCompatibleLaneInsteadOfAddingTheBusyLaneRemainingTime() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(60));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        QueueEntry activeWork = entry(serviceType);
        UUID stageId = target.getServiceStage().getId();
        Resource busyResource = Resource.create("Busy A lane", Set.of(serviceType));
        Resource availableResource = Resource.create("Available A lane", Set.of(serviceType));
        set(busyResource, "id", UUID.randomUUID());
        set(availableResource, "id", UUID.randomUUID());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Assignment activeAssignment = Assignment.assign(
                activeWork.getServiceStage(), busyResource, now.minus(Duration.ofMinutes(40)), null);
        activeAssignment.start(now.minus(Duration.ofMinutes(40)));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of(availableResource));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of(busyResource));
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of(activeAssignment));
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(target));

        assertThat(service().estimateMinutes(stageId)).isZero();
    }

    @Test
    void includesCompatibleQueuedWorkScheduledAheadOfTheTarget() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(30));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        QueueEntry ahead = entry(serviceType);
        set(ahead.getServiceStage(), "predictedServiceDuration", Duration.ofMinutes(30));
        UUID stageId = target.getServiceStage().getId();
        Resource availableResource = Resource.create("A lane", Set.of(serviceType));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of(availableResource));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of());
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(ahead, target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(ahead), Optional.of(target));

        assertThat(service().estimateMinutes(stageId)).isEqualTo(30);
    }

    @Test
    void includesCriticalCompatibleWorkSelectedAheadOfANormalTarget() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(30));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        ServiceRequest criticalRequest = ServiceRequest.create(
                Vehicle.create(UUID.randomUUID(), "KA02AB1234", "T", "M", 2024),
                serviceType,
                PriorityClass.CRITICAL,
                null);
        ServiceWorkflow criticalWorkflow = ServiceWorkflow.create(criticalRequest);
        var criticalStage = criticalWorkflow.addStage(serviceType, Duration.ofMinutes(15));
        set(criticalStage, "id", UUID.randomUUID());
        QueueEntry critical = QueueEntry.enter(criticalStage, Instant.parse("2025-12-31T23:59:00Z"));
        UUID stageId = target.getServiceStage().getId();
        Resource availableResource = Resource.create("A lane", Set.of(serviceType));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of(availableResource));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of());
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target, critical)));

        WaitTimeCalculationService calculationService = new WaitTimeCalculationService(
                queues, resources, assignments, new HybridSchedulerStrategy(clock), clock);

        assertThat(calculationService.estimateMinutes(stageId)).isEqualTo(15);
    }

    @Test
    void includesOlderNormalCompatibleWorkSelectedAheadThroughAging() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(30));
        set(serviceType, "id", UUID.randomUUID());
        ServiceRequest targetRequest = ServiceRequest.create(
                Vehicle.create(UUID.randomUUID(), "KA03AB1234", "T", "M", 2024), serviceType, PriorityClass.NORMAL, null);
        var targetWorkflow = ServiceWorkflow.create(targetRequest);
        var targetStage = targetWorkflow.addStage(serviceType, Duration.ofMinutes(10));
        set(targetStage, "id", UUID.randomUUID());
        QueueEntry target = QueueEntry.enter(targetStage, Instant.parse("2025-12-31T23:59:00Z"));
        ServiceRequest olderRequest = ServiceRequest.create(
                Vehicle.create(UUID.randomUUID(), "KA04AB1234", "T", "M", 2024), serviceType, PriorityClass.NORMAL, null);
        var olderWorkflow = ServiceWorkflow.create(olderRequest);
        var olderStage = olderWorkflow.addStage(serviceType, Duration.ofMinutes(30));
        set(olderStage, "id", UUID.randomUUID());
        QueueEntry older = QueueEntry.enter(olderStage, Instant.parse("2025-12-31T22:00:00Z"));
        UUID stageId = targetStage.getId();
        Resource availableResource = Resource.create("A lane", Set.of(serviceType));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of(availableResource));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of());
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target, older)));

        WaitTimeCalculationService calculationService = new WaitTimeCalculationService(
                queues, resources, assignments, new HybridSchedulerStrategy(clock), clock);

        assertThat(calculationService.estimateMinutes(stageId)).isEqualTo(30);
    }

    @Test
    void fallsBackToTheServiceTypeDefaultDurationWhenAnActiveAssignmentHasNoPrediction() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(45));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        QueueEntry activeWork = entry(serviceType);
        set(activeWork.getServiceStage(), "predictedServiceDuration", null);
        UUID stageId = target.getServiceStage().getId();
        Resource busyResource = Resource.create("A lane", Set.of(serviceType));
        set(busyResource, "id", UUID.randomUUID());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Assignment activeAssignment = Assignment.assign(
                activeWork.getServiceStage(), busyResource, now.minus(Duration.ofMinutes(15)), null);
        activeAssignment.start(now.minus(Duration.ofMinutes(15)));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of());
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of(busyResource));
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of(activeAssignment));
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(target));

        assertThat(service().estimateMinutes(stageId)).isEqualTo(30);
    }

    @Test
    void clampsAnOverdueActiveAssignmentRemainingDurationToZero() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(45));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        QueueEntry activeWork = entry(serviceType);
        set(activeWork.getServiceStage(), "predictedServiceDuration", Duration.ofMinutes(20));
        UUID stageId = target.getServiceStage().getId();
        Resource busyResource = Resource.create("A lane", Set.of(serviceType));
        set(busyResource, "id", UUID.randomUUID());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Assignment activeAssignment = Assignment.assign(
                activeWork.getServiceStage(), busyResource, now.minus(Duration.ofMinutes(30)), Duration.ofMinutes(20));
        activeAssignment.start(now.minus(Duration.ofMinutes(30)));

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of());
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of(busyResource));
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of(activeAssignment));
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(target));

        assertThat(service().estimateMinutes(stageId)).isZero();
    }

    @Test
    void rejectsATargetWithoutAWaitingQueueEntry() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(30));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        UUID stageId = target.getServiceStage().getId();

        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().estimateMinutes(stageId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void doesNotMutateQueueStageAssignmentOrResourceStateWhileCalculating() throws Exception {
        ServiceType serviceType = ServiceType.create("A", null, Duration.ofMinutes(60));
        set(serviceType, "id", UUID.randomUUID());
        QueueEntry target = entry(serviceType);
        QueueEntry activeWork = entry(serviceType);
        UUID stageId = target.getServiceStage().getId();
        Resource busyResource = Resource.create("A lane", Set.of(serviceType));
        set(busyResource, "id", UUID.randomUUID());
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Assignment activeAssignment = Assignment.assign(
                activeWork.getServiceStage(), busyResource, now.minus(Duration.ofMinutes(40)), null);
        activeAssignment.start(now.minus(Duration.ofMinutes(40)));

        var queueStatus = target.getStatus();
        var stageStatus = target.getServiceStage().getStatus();
        var assignmentStatus = activeAssignment.getStatus();
        var resourceStatus = busyResource.getStatus();
        when(queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING)).thenReturn(Optional.of(target));
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE, serviceType.getId()))
                .thenReturn(List.of());
        when(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY, serviceType.getId()))
                .thenReturn(List.of(busyResource));
        when(assignments.findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus.IN_PROGRESS))
                .thenReturn(List.of(activeAssignment));
        when(queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING))
                .thenReturn(new java.util.ArrayList<>(List.of(target)));
        when(scheduler.selectNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(target));

        service().estimateMinutes(stageId);

        assertThat(target.getStatus()).isEqualTo(queueStatus);
        assertThat(target.getServiceStage().getStatus()).isEqualTo(stageStatus);
        assertThat(activeAssignment.getStatus()).isEqualTo(assignmentStatus);
        assertThat(busyResource.getStatus()).isEqualTo(resourceStatus);
    }
    private WaitTimeCalculationService service(){return new WaitTimeCalculationService(queues,resources,assignments,scheduler,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC));}
    private QueueEntry entry(ServiceType type)throws Exception{ServiceRequest r=ServiceRequest.create(Vehicle.create(UUID.randomUUID(),"KA01AB1234","T","M",2024),type,PriorityClass.NORMAL,null);ServiceWorkflow w=ServiceWorkflow.create(r);var s=w.addStage(type,Duration.ofMinutes(10));set(s,"id",UUID.randomUUID());return QueueEntry.enter(s,Instant.now());}
    private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
}
