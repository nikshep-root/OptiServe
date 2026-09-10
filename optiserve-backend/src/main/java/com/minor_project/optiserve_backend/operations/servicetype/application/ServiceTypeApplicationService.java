package com.minor_project.optiserve_backend.operations.servicetype.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.servicetype.api.CreateServiceTypeRequest;
import com.minor_project.optiserve_backend.operations.servicetype.api.ServiceTypeResponse;
import com.minor_project.optiserve_backend.operations.servicetype.api.UpdateServiceTypeRequest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceTypeApplicationService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ResourceRepository resourceRepository;

    public ServiceTypeApplicationService(
            ServiceTypeRepository serviceTypeRepository,
            ServiceRequestRepository serviceRequestRepository,
            ResourceRepository resourceRepository) {
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public ServiceTypeResponse create(CreateServiceTypeRequest request) {
        String name = normalizedName(request.name());
        if (serviceTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A service type with this name already exists.");
        }

        ServiceType serviceType = ServiceType.create(
                name,
                request.description(),
                Duration.ofSeconds(request.defaultServiceDurationSeconds()));
        if (!request.active()) {
            serviceType.deactivate();
        }
        return toResponse(serviceTypeRepository.save(serviceType));
    }

    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> findAll() {
        return serviceTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(ServiceTypeApplicationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceTypeResponse findById(UUID id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public ServiceTypeResponse update(UUID id, UpdateServiceTypeRequest request) {
        ServiceType serviceType = findEntity(id);
        String name = request.name() == null ? serviceType.getName() : normalizedName(request.name());
        if (serviceTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A service type with this name already exists.");
        }

        String description = request.description() == null ? serviceType.getDescription() : request.description();
        Duration duration = request.defaultServiceDurationSeconds() == null
                ? serviceType.getDefaultServiceDuration()
                : Duration.ofSeconds(request.defaultServiceDurationSeconds());
        serviceType.update(name, description, duration);
        if (request.active() != null) {
            if (request.active()) {
                serviceType.activate();
            } else {
                serviceType.deactivate();
            }
        }
        return toResponse(serviceType);
    }

    @Transactional
    public void delete(UUID id) {
        ServiceType serviceType = findEntity(id);
        if (serviceRequestRepository.existsByServiceTypeId(id)
                || resourceRepository.existsByCompatibleServiceTypes_Id(id)) {
            serviceType.deactivate();
            return;
        }
        serviceTypeRepository.delete(serviceType);
    }

    private ServiceType findEntity(UUID id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type was not found."));
    }

    private static ServiceTypeResponse toResponse(ServiceType serviceType) {
        return new ServiceTypeResponse(
                serviceType.getId(),
                serviceType.getName(),
                serviceType.getDescription(),
                serviceType.getDefaultServiceDuration().toSeconds(),
                serviceType.isActive(),
                serviceType.getCreatedAt(),
                serviceType.getUpdatedAt());
    }

    private static String normalizedName(String name) {
        return name == null ? null : name.trim();
    }
}
