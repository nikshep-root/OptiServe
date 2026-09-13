package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    boolean existsByCompatibleServiceTypes_Id(UUID serviceTypeId);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<Resource> findByStatusAndCompatibleServiceTypes_Id(ResourceStatus status, UUID serviceTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resource from Resource resource join resource.compatibleServiceTypes serviceType "
            + "where resource.status = :status and serviceType.id = :serviceTypeId "
            + "order by resource.name asc, resource.id asc")
    List<Resource> findByStatusAndCompatibleServiceTypesIdForUpdate(
            @Param("status") ResourceStatus status,
            @Param("serviceTypeId") UUID serviceTypeId);
}
