package com.minor_project.optiserve_backend.operations.automation.persistence;

import com.minor_project.optiserve_backend.operations.automation.domain.WorkshopAutomationSettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkshopAutomationSettingsRepository extends JpaRepository<WorkshopAutomationSettings, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settings from WorkshopAutomationSettings settings where settings.id = :id")
    Optional<WorkshopAutomationSettings> findSingletonForUpdate(@Param("id") short id);
}
