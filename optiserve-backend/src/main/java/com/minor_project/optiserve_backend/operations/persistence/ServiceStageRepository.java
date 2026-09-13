package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceStageRepository extends JpaRepository<ServiceStage, UUID> {

    List<ServiceStage> findByWorkflowIdOrderBySequenceNumberAsc(UUID workflowId);

    List<ServiceStage> findByStatusInOrderByEligibleAtAsc(List<ServiceStageStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stage from ServiceStage stage where stage.id = :id")
    Optional<ServiceStage> findByIdForUpdate(@Param("id") UUID id);
}
