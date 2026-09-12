package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByVin(String vin);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    List<Vehicle> findByCustomerId(UUID customerId);

    boolean existsByVin(String vin);

    boolean existsByRegistrationNumber(String registrationNumber);
}
