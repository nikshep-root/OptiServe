package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {
}
