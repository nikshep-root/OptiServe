package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceStageRepository extends JpaRepository<ServiceStage, UUID> {

    List<ServiceStage> findByWorkflowIdOrderBySequenceNumberAsc(UUID workflowId);

    List<ServiceStage> findByStatusInOrderByEligibleAtAsc(List<ServiceStageStatus> statuses);
}
