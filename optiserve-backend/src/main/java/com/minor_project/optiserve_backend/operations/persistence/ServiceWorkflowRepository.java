package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceWorkflowRepository extends JpaRepository<ServiceWorkflow, UUID> {

    Optional<ServiceWorkflow> findByServiceRequestId(UUID serviceRequestId);
}
