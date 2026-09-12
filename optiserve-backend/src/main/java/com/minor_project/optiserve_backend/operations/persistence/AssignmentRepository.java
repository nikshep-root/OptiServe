package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Assignment;
import com.minor_project.optiserve_backend.operations.domain.AssignmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByServiceRequestIdAndStatusIn(UUID serviceRequestId, Collection<AssignmentStatus> statuses);

    Optional<Assignment> findByMechanicIdAndStatusIn(UUID mechanicId, Collection<AssignmentStatus> statuses);

    Optional<Assignment> findByServiceBayIdAndStatusIn(UUID bayId, Collection<AssignmentStatus> statuses);

    @Query("""
        SELECT a FROM Assignment a
        JOIN a.serviceRequest sr
        JOIN sr.vehicle v
        WHERE v.vin = :vin AND a.status = :status
        ORDER BY a.completedAt DESC
    """)
    List<Assignment> findByVehicleVinAndStatus(
            @Param("vin") String vin,
            @Param("status") AssignmentStatus status);
}
