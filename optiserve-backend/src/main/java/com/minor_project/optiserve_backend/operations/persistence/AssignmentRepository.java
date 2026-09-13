package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    boolean existsByResourceId(UUID resourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from Assignment assignment where assignment.id = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") UUID id);
}
