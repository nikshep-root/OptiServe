package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.BayStatus;
import com.minor_project.optiserve_backend.operations.domain.BayType;
import com.minor_project.optiserve_backend.operations.domain.ServiceBay;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceBayRepository extends JpaRepository<ServiceBay, UUID> {

    Optional<ServiceBay> findByBayNumber(String bayNumber);

    List<ServiceBay> findByStatus(BayStatus status);

    List<ServiceBay> findByBayType(BayType bayType);

    boolean existsByBayNumber(String bayNumber);
}
