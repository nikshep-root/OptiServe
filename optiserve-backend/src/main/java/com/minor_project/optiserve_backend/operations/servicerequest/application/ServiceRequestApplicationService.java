package com.minor_project.optiserve_backend.operations.servicerequest.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceDurationPredictionSource;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.duration.application.DefaultServiceDurationPredictor;
import com.minor_project.optiserve_backend.operations.duration.application.ServiceDurationPredictor;
import com.minor_project.optiserve_backend.operations.duration.application.ServiceDurationPrediction;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceRequestApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRequestApplicationService.class);

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceWorkflowRepository serviceWorkflowRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceDurationPredictor serviceDurationPredictor;
    private final DefaultServiceDurationPredictor defaultServiceDurationPredictor;

    public ServiceRequestApplicationService(
            ServiceRequestRepository serviceRequestRepository,
            ServiceWorkflowRepository serviceWorkflowRepository,
            VehicleRepository vehicleRepository,
            ServiceTypeRepository serviceTypeRepository,
            ServiceDurationPredictor serviceDurationPredictor,
            DefaultServiceDurationPredictor defaultServiceDurationPredictor) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.serviceWorkflowRepository = serviceWorkflowRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceDurationPredictor = serviceDurationPredictor;
        this.defaultServiceDurationPredictor = defaultServiceDurationPredictor;
    }

    @Transactional
    public ServiceRequestResponse create(CreateServiceRequestRequest request) {
        return create(request, findVehicle(request.vehicleId()));
    }

    @Transactional
    public ServiceRequestResponse createForCustomer(CreateServiceRequestRequest request, String email) {
        Vehicle vehicle = vehicleRepository.findByIdAndCustomer_User_EmailIgnoreCase(request.vehicleId(), email)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle was not found."));
        return create(request, vehicle);
    }

    private ServiceRequestResponse create(CreateServiceRequestRequest request, Vehicle vehicle) {
        List<ServiceType> stageServiceTypes = findActiveServiceTypes(request.serviceTypeIds());

        ServiceRequest serviceRequest = ServiceRequest.create(
                vehicle,
                stageServiceTypes.getFirst(),
                request.priority(),
                request.appointmentTime());
        ServiceWorkflow workflow = ServiceWorkflow.create(serviceRequest);
        stageServiceTypes.forEach(serviceType -> {
            ServiceStage stage = workflow.addStage(serviceType, null);
            ServiceDurationPrediction prediction = predictSafely(stage);
            stage.updatePredictedServiceDuration(prediction.duration(), prediction.source());
        });

        ServiceRequest persistedRequest = serviceRequestRepository.save(serviceRequest);
        ServiceWorkflow persistedWorkflow = serviceWorkflowRepository.save(workflow);

        return toResponse(persistedRequest, persistedWorkflow);
    }

    private ServiceDurationPrediction predictSafely(ServiceStage stage) {
        try {
            ServiceDurationPrediction prediction = serviceDurationPredictor.predictWithProvenance(stage);
            if (prediction != null && prediction.duration() != null
                    && !prediction.duration().isNegative() && !prediction.duration().isZero()
                    && prediction.source() != null) {
                return prediction;
            }
            LOGGER.warn("Service duration predictor returned an invalid value; using the configured default");
        } catch (RuntimeException exception) {
            LOGGER.warn("Service duration predictor failed; using the configured default ({})",
                    exception.getClass().getSimpleName());
        }
        return new ServiceDurationPrediction(
                defaultServiceDurationPredictor.predict(stage),
                ServiceDurationPredictionSource.DEFAULT_FALLBACK);
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse findById(UUID id) {
        ServiceRequest serviceRequest = serviceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service request was not found."));
        return toResponse(serviceRequest, findWorkflow(id));
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse findByIdForCustomer(UUID id, String email) {
        ServiceRequest serviceRequest = serviceRequestRepository
                .findByIdAndVehicle_Customer_User_EmailIgnoreCase(id, email)
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

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> findAllForCustomer(String email) {
        return serviceRequestRepository.findAllByVehicle_Customer_User_EmailIgnoreCaseOrderByRequestedAtDesc(email)
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
                stage.getStatus(),
                stage.getPredictedServiceDuration(),
                stage.getPredictionSource());
    }
}
