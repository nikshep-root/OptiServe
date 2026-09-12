package com.minor_project.optiserve_backend.operations.resource.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.common.api.StateConflictException;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.resource.api.CompatibleServiceTypeResponse;
import com.minor_project.optiserve_backend.operations.resource.api.CreateResourceRequest;
import com.minor_project.optiserve_backend.operations.resource.api.ResourceResponse;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceRequest;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceStatusRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceApplicationService {

    private final ResourceRepository resourceRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final AssignmentRepository assignmentRepository;

    public ResourceApplicationService(
            ResourceRepository resourceRepository,
            ServiceTypeRepository serviceTypeRepository,
            AssignmentRepository assignmentRepository) {
        this.resourceRepository = resourceRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public ResourceResponse create(CreateResourceRequest request) {
        String name = normalizedName(request.name());
        if (resourceRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A resource with this name already exists.");
        }
        return toResponse(resourceRepository.save(Resource.create(name, Set.of())));
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> findAll() {
        return resourceRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(ResourceApplicationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse findById(UUID id) {
        return toResponse(findResource(id));
    }

    @Transactional
    public ResourceResponse update(UUID id, UpdateResourceRequest request) {
        Resource resource = findResource(id);
        if (request.name() != null) {
            String name = normalizedName(request.name());
            if (resourceRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ConflictException("A resource with this name already exists.");
            }
            resource.updateName(name);
        }
        return toResponse(resource);
    }

    @Transactional
    public ResourceResponse changeStatus(UUID id, UpdateResourceStatusRequest request) {
        Resource resource = findResource(id);
        try {
            resource.changeStatus(request.status());
        } catch (IllegalStateException exception) {
            throw new StateConflictException("The resource cannot transition to the requested status.", exception);
        }
        return toResponse(resource);
    }

    @Transactional
    public ResourceResponse addCompatibility(UUID resourceId, UUID serviceTypeId) {
        Resource resource = findResource(resourceId);
        ServiceType serviceType = findServiceType(serviceTypeId);
        if (resource.supports(serviceType)) {
            throw new ConflictException("The service type is already compatible with this resource.");
        }
        resource.addCapability(serviceType);
        return toResponse(resource);
    }

    @Transactional
    public void removeCompatibility(UUID resourceId, UUID serviceTypeId) {
        Resource resource = findResource(resourceId);
        ServiceType serviceType = findServiceType(serviceTypeId);
        if (!resource.supports(serviceType)) {
            throw new ResourceNotFoundException("Service type compatibility was not found.");
        }
        resource.removeCapability(serviceType);
    }

    @Transactional(readOnly = true)
    public List<CompatibleServiceTypeResponse> findCompatibleServiceTypes(UUID resourceId) {
        return toResponse(findResource(resourceId)).compatibleServiceTypes();
    }

    @Transactional
    public void delete(UUID id) {
        Resource resource = findResource(id);
        if (assignmentRepository.existsByResourceId(id)) {
            resource.markOffline();
            return;
        }
        resourceRepository.delete(resource);
    }

    private Resource findResource(UUID id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource was not found."));
    }

    private ServiceType findServiceType(UUID id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type was not found."));
    }

    private static ResourceResponse toResponse(Resource resource) {
        List<CompatibleServiceTypeResponse> compatibleServiceTypes = resource.getCompatibleServiceTypes().stream()
                .map(serviceType -> new CompatibleServiceTypeResponse(
                        serviceType.getId(), serviceType.getName(), serviceType.isActive()))
                .sorted(Comparator.comparing(CompatibleServiceTypeResponse::name))
                .toList();
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getStatus(),
                compatibleServiceTypes,
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    private static String normalizedName(String name) {
        return name == null ? null : name.trim();
    }
}
