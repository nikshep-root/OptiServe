package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Mechanic;
import com.minor_project.optiserve_backend.operations.domain.MechanicStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MechanicRepository extends JpaRepository<Mechanic, UUID> {

    Optional<Mechanic> findByEmployeeId(String employeeId);

    List<Mechanic> findByStatus(MechanicStatus status);

    boolean existsByEmployeeId(String employeeId);
}
