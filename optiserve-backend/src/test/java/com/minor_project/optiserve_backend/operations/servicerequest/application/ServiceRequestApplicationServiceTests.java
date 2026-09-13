package com.minor_project.optiserve_backend.operations.servicerequest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceWorkflowRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.servicerequest.api.CreateServiceRequestRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceRequestApplicationServiceTests {

    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ServiceWorkflowRepository serviceWorkflowRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ServiceTypeRepository serviceTypeRepository;

    @Test
    void createsRequestWithActiveWorkflowAndOrderedStages() {
        UUID vehicleId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        UUID repairId = UUID.randomUUID();
        Vehicle vehicle = vehicle();
        ServiceType inspection = serviceType("Inspection");
        ServiceType repair = serviceType("Repair");
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(serviceTypeRepository.findById(inspectionId)).thenReturn(Optional.of(inspection));
        when(serviceTypeRepository.findById(repairId)).thenReturn(Optional.of(repair));
        when(serviceRequestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceWorkflowRepository.save(any(ServiceWorkflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(new CreateServiceRequestRequest(
                vehicleId, PriorityClass.URGENT, null, List.of(inspectionId, repairId)));

        ArgumentCaptor<ServiceRequest> requestCaptor = ArgumentCaptor.forClass(ServiceRequest.class);
        ArgumentCaptor<ServiceWorkflow> workflowCaptor = ArgumentCaptor.forClass(ServiceWorkflow.class);
        verify(serviceRequestRepository).save(requestCaptor.capture());
        verify(serviceWorkflowRepository).save(workflowCaptor.capture());
        assertThat(requestCaptor.getValue().getVehicle()).isSameAs(vehicle);
        assertThat(requestCaptor.getValue().getServiceType()).isSameAs(inspection);
        assertThat(workflowCaptor.getValue().getStages())
                .extracting(stage -> stage.getSequenceNumber(), stage -> stage.getStatus(), stage -> stage.getServiceType())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, ServiceStageStatus.ELIGIBLE, inspection),
                        org.assertj.core.groups.Tuple.tuple(2, ServiceStageStatus.PENDING, repair));
    }

    @Test
    void doesNotPersistAnythingWhenVehicleOrServiceTypeIsInvalid() {
        UUID vehicleId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(new CreateServiceRequestRequest(
                vehicleId, PriorityClass.NORMAL, null, List.of(serviceTypeId))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(serviceRequestRepository, never()).save(any());
        verify(serviceWorkflowRepository, never()).save(any());

        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle()));
        when(serviceTypeRepository.findById(serviceTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(new CreateServiceRequestRequest(
                vehicleId, PriorityClass.NORMAL, null, List.of(serviceTypeId))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(serviceRequestRepository, never()).save(any());
        verify(serviceWorkflowRepository, never()).save(any());
    }

    private ServiceRequestApplicationService service() {
        return new ServiceRequestApplicationService(
                serviceRequestRepository,
                serviceWorkflowRepository,
                vehicleRepository,
                serviceTypeRepository);
    }

    private Vehicle vehicle() {
        return Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024);
    }

    private ServiceType serviceType(String name) {
        return ServiceType.create(name, null, Duration.ofMinutes(15));
    }
}
