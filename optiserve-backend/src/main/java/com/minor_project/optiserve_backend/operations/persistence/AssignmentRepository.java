package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    boolean existsByResourceId(UUID resourceId);

    boolean existsByResourceIdAndStatusIn(
            UUID resourceId,
            List<AssignmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from Assignment assignment where assignment.id = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") UUID id);

    List<Assignment> findByStatus(com.minor_project.optiserve_backend.operations.domain.AssignmentStatus status);

    @EntityGraph(attributePaths = {"serviceRequest", "serviceStage", "resource"})
    List<Assignment> findAllByOrderByAssignedAtDesc();

    List<Assignment> findByServiceRequest_Vehicle_Customer_User_EmailIgnoreCaseOrderByAssignedAtDesc(String email);
}
