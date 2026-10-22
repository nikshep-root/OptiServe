package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    boolean existsByServiceTypeId(UUID serviceTypeId);

    List<ServiceRequest> findAllByVehicle_Customer_User_EmailIgnoreCaseOrderByRequestedAtDesc(String email);

    Optional<ServiceRequest> findByIdAndVehicle_Customer_User_EmailIgnoreCase(UUID id, String email);

    boolean existsByVehicle_Id(UUID vehicleId);
}
