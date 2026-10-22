package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    Optional<Vehicle> findByIdAndCustomer_User_EmailIgnoreCase(UUID id, String email);

    boolean existsByCustomer_Id(UUID customerId);
}
