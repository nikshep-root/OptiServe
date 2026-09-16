package com.minor_project.optiserve_backend.operations.waittime.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulerStrategy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitTimeCalculationService {
    private final QueueEntryRepository queues; private final ResourceRepository resources;
    private final AssignmentRepository assignments; private final SchedulerStrategy scheduler; private final Clock clock;
    public WaitTimeCalculationService(QueueEntryRepository queues, ResourceRepository resources, AssignmentRepository assignments, SchedulerStrategy scheduler, Clock clock) {
        this.queues=queues; this.resources=resources; this.assignments=assignments; this.scheduler=scheduler; this.clock=clock;
    }
    @Transactional(readOnly = true)
    public long estimateMinutes(UUID stageId) {
        QueueEntry target=queues.findByServiceStageIdAndStatus(stageId, QueueEntryStatus.WAITING).orElseThrow(()->new ResourceNotFoundException("Waiting queue entry was not found."));
        if (target.getServiceStage().getStatus()!=com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus.QUEUED) throw new ConflictException("Only a queued stage has a wait estimate.");
        List<Resource> lanes=resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.AVAILABLE,target.getServiceStage().getServiceType().getId());
        lanes=new java.util.ArrayList<>(lanes); lanes.addAll(resources.findByStatusAndCompatibleServiceTypes_Id(ResourceStatus.BUSY,target.getServiceStage().getServiceType().getId()));
        if(lanes.isEmpty()) return 0;
        List<QueueEntry> ordered=queues.findByStatusOrderByQueuedAtAsc(QueueEntryStatus.WAITING); long[] available=lanes.stream().mapToLong(this::availability).toArray();
        while(!ordered.isEmpty()) { java.util.Optional<QueueEntry> selected=scheduler.selectNext(ordered); if(selected.isEmpty()) break; QueueEntry next=selected.get(); if(next==target) return java.util.Arrays.stream(available).min().orElse(0); int lane=-1; for(int i=0;i<available.length;i++) if(lanes.get(i).supports(next.getServiceStage().getServiceType()) && (lane<0||available[i]<available[lane])) lane=i; if(lane>=0) available[lane]+=duration(next); ordered.remove(next); }
        return java.util.Arrays.stream(available).min().orElse(0);
    }
    private long availability(Resource resource){ return assignments.findByStatus(AssignmentStatus.IN_PROGRESS).stream().filter(a->a.getResource().getId().equals(resource.getId())).findFirst().map(this::remaining).orElse(0L); }
    private long remaining(Assignment a){ Duration d=a.getPredictedServiceDuration()!=null?a.getPredictedServiceDuration():a.getServiceStage().getServiceType().getDefaultServiceDuration(); return Math.max(0,d.minus(Duration.between(a.getStartedAt(),Instant.now(clock))).toMinutes()); }
    private long duration(QueueEntry q){ Duration d=q.getServiceStage().getPredictedServiceDuration(); return (d==null?q.getServiceStage().getServiceType().getDefaultServiceDuration():d).toMinutes(); }
}
