package com.minor_project.optiserve_backend.operations.assignment.application;

import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResponse;
import com.minor_project.optiserve_backend.operations.assignment.api.AssignmentNextResult;
import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceStageRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentApplicationService {

    private final QueueEntryRepository queueEntryRepository;
    private final ServiceStageRepository serviceStageRepository;
    private final ResourceRepository resourceRepository;
    private final AssignmentRepository assignmentRepository;
    private final SchedulerStrategy schedulerStrategy;
    private final Clock clock;

    public AssignmentApplicationService(
            QueueEntryRepository queueEntryRepository,
            ServiceStageRepository serviceStageRepository,
            ResourceRepository resourceRepository,
            AssignmentRepository assignmentRepository,
            SchedulerStrategy schedulerStrategy,
            Clock clock) {
        this.queueEntryRepository = queueEntryRepository;
        this.serviceStageRepository = serviceStageRepository;
        this.resourceRepository = resourceRepository;
        this.assignmentRepository = assignmentRepository;
        this.schedulerStrategy = schedulerStrategy;
        this.clock = clock;
    }

    @Transactional
    public AssignmentNextResponse assignNext() {
        Optional<QueueEntry> candidate = schedulerStrategy.selectNext(
                queueEntryRepository.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING));
        if (candidate.isEmpty()) {
            return noAssignment(AssignmentNextResult.NO_QUEUED_STAGE);
        }

        ServiceStage stage = serviceStageRepository.findByIdForUpdate(candidate.orElseThrow().getServiceStage().getId())
                .orElse(null);
        if (stage == null || stage.getStatus() != ServiceStageStatus.QUEUED) {
            return noAssignment(AssignmentNextResult.NO_QUEUED_STAGE);
        }
        QueueEntry queueEntry = queueEntryRepository.findByServiceStageIdAndStatus(stage.getId(), QueueEntryStatus.WAITING)
                .orElse(null);
        if (queueEntry == null) {
            return noAssignment(AssignmentNextResult.NO_QUEUED_STAGE);
        }

        List<Resource> resources = resourceRepository.findByStatusAndCompatibleServiceTypesIdForUpdate(
                ResourceStatus.AVAILABLE, stage.getServiceType().getId());
        if (resources.isEmpty()) {
            return noAssignment(AssignmentNextResult.NO_COMPATIBLE_RESOURCE);
        }

        Resource resource = resources.getFirst();
        Assignment assignment = Assignment.assign(stage, resource, Instant.now(clock), stage.getPredictedServiceDuration());
        queueEntry.remove();
        Assignment persistedAssignment = assignmentRepository.saveAndFlush(assignment);
        return assigned(persistedAssignment);
    }

    private static AssignmentNextResponse noAssignment(AssignmentNextResult result) {
        return new AssignmentNextResponse(result, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static AssignmentNextResponse assigned(Assignment assignment) {
        ServiceStage stage = assignment.getServiceStage();
        return new AssignmentNextResponse(
                AssignmentNextResult.ASSIGNED,
                assignment.getId(),
                assignment.getServiceRequest().getId(),
                assignment.getServiceRequest().getVehicle().getId(),
                assignment.getServiceRequest().getVehicle().getRegistrationNumber(),
                stage.getWorkflow().getId(),
                stage.getId(),
                stage.getServiceType().getId(),
                stage.getServiceType().getName(),
                assignment.getServiceRequest().getPriorityClass(),
                assignment.getResource().getId(),
                assignment.getResource().getName(),
                assignment.getStatus());
    }
}
