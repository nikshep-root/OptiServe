package com.minor_project.optiserve_backend.operations.servicerequest.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestResponse;
import com.minor_project.optiserve_backend.operations.servicerequest.api.ServiceRequestStageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceRequestApplicationService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceWorkflowRepository serviceWorkflowRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public ServiceRequestApplicationService(
            ServiceRequestRepository serviceRequestRepository,
            ServiceWorkflowRepository serviceWorkflowRepository,
            VehicleRepository vehicleRepository,
            ServiceTypeRepository serviceTypeRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.serviceWorkflowRepository = serviceWorkflowRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceTypeRepository = serviceTypeRepository;
    }

    @Transactional
    public ServiceRequestResponse create(CreateServiceRequestRequest request) {
        Vehicle vehicle = findVehicle(request.vehicleId());
        List<ServiceType> stageServiceTypes = findActiveServiceTypes(request.serviceTypeIds());

        ServiceRequest serviceRequest = ServiceRequest.create(
                vehicle,
                stageServiceTypes.getFirst(),
                request.priority(),
                request.appointmentTime());
        ServiceRequest persistedRequest = serviceRequestRepository.save(serviceRequest);

        ServiceWorkflow workflow = ServiceWorkflow.create(persistedRequest);
        stageServiceTypes.forEach(serviceType -> workflow.addStage(serviceType, null));
        ServiceWorkflow persistedWorkflow = serviceWorkflowRepository.save(workflow);

        return toResponse(persistedRequest, persistedWorkflow);
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse findById(UUID id) {
        ServiceRequest serviceRequest = serviceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service request was not found."));
        return toResponse(serviceRequest, findWorkflow(id));
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> findAll() {
        return serviceRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "requestedAt"))
                .stream()
                .map(serviceRequest -> toResponse(serviceRequest, findWorkflow(serviceRequest.getId())))
                .toList();
    }

    private Vehicle findVehicle(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle was not found."));
    }

    private List<ServiceType> findActiveServiceTypes(List<UUID> serviceTypeIds) {
        List<ServiceType> serviceTypes = new ArrayList<>();
        for (UUID serviceTypeId : serviceTypeIds) {
            ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service type was not found."));
            if (!serviceType.isActive()) {
                throw new ConflictException("An inactive service type cannot be used for a new request.");
            }
            serviceTypes.add(serviceType);
        }
        return serviceTypes;
    }

    private ServiceWorkflow findWorkflow(UUID serviceRequestId) {
        return serviceWorkflowRepository.findByServiceRequestId(serviceRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Service workflow was not found."));
    }

    private static ServiceRequestResponse toResponse(ServiceRequest serviceRequest, ServiceWorkflow workflow) {
        List<ServiceRequestStageResponse> stages = workflow.getStages().stream()
                .map(ServiceRequestApplicationService::toStageResponse)
                .toList();
        return new ServiceRequestResponse(
                serviceRequest.getId(),
                serviceRequest.getVehicle().getId(),
                serviceRequest.getVehicle().getRegistrationNumber(),
                serviceRequest.getPriorityClass(),
                serviceRequest.getAppointmentAt(),
                serviceRequest.getStatus(),
                workflow.getId(),
                workflow.getStatus(),
                stages);
    }

    private static ServiceRequestStageResponse toStageResponse(ServiceStage stage) {
        return new ServiceRequestStageResponse(
                stage.getId(),
                stage.getSequenceNumber(),
                stage.getServiceType().getId(),
                stage.getServiceType().getName(),
                stage.getStatus());
    }
}
