package com.minor_project.optiserve_backend.operations.servicetype.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.servicetype.api.CreateServiceTypeRequest;
import com.minor_project.optiserve_backend.operations.servicetype.api.UpdateServiceTypeRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceTypeApplicationServiceTests {

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Test
    void createsAServiceTypeWithNormalizedNameAndRequestedActiveState() {
        ServiceTypeApplicationService service = service();
        when(serviceTypeRepository.save(any(ServiceType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateServiceTypeRequest("  Registration  ", "Desk service", 900L, false));

        ArgumentCaptor<ServiceType> saved = ArgumentCaptor.forClass(ServiceType.class);
        verify(serviceTypeRepository).save(saved.capture());
        assertThat(saved.getValue())
                .extracting(ServiceType::getName, ServiceType::getDefaultServiceDuration, ServiceType::isActive)
                .containsExactly("Registration", Duration.ofMinutes(15), false);
        assertThat(response.defaultServiceDurationSeconds()).isEqualTo(900L);
    }

    @Test
    void rejectsDuplicateNamesIgnoringCase() {
        ServiceTypeApplicationService service = service();
        when(serviceTypeRepository.existsByNameIgnoreCase("Registration")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateServiceTypeRequest("Registration", null, 900L, true)))
                .isInstanceOf(ConflictException.class);

        verify(serviceTypeRepository, never()).save(any());
    }

    @Test
    void updatesMutableServiceTypeProperties() {
        UUID id = UUID.randomUUID();
        ServiceType serviceType = ServiceType.create("Registration", "Original", Duration.ofMinutes(15));
        when(serviceTypeRepository.findById(id)).thenReturn(Optional.of(serviceType));

        var response = service().update(id, new UpdateServiceTypeRequest("Updated registration", "Updated", 1200L, false));

        assertThat(response)
                .extracting(
                        value -> value.name(),
                        value -> value.description(),
                        value -> value.defaultServiceDurationSeconds(),
                        value -> value.active())
                .containsExactly("Updated registration", "Updated", 1200L, false);
    }

    @Test
    void reportsNotFoundForUnknownServiceType() {
        UUID id = UUID.randomUUID();
        when(serviceTypeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesUnreferencedServiceTypesAndDeactivatesReferencedTypes() {
        UUID deletableId = UUID.randomUUID();
        ServiceType deletable = ServiceType.create("Deletable", null, Duration.ofMinutes(10));
        when(serviceTypeRepository.findById(deletableId)).thenReturn(Optional.of(deletable));
        when(serviceRequestRepository.existsByServiceTypeId(deletableId)).thenReturn(false);
        when(resourceRepository.existsByCompatibleServiceTypes_Id(deletableId)).thenReturn(false);

        service().delete(deletableId);

        verify(serviceTypeRepository).delete(deletable);

        UUID referencedId = UUID.randomUUID();
        ServiceType referenced = ServiceType.create("Referenced", null, Duration.ofMinutes(10));
        when(serviceTypeRepository.findById(referencedId)).thenReturn(Optional.of(referenced));
        when(serviceRequestRepository.existsByServiceTypeId(referencedId)).thenReturn(true);

        service().delete(referencedId);

        assertThat(referenced.isActive()).isFalse();
        verify(serviceTypeRepository, never()).delete(referenced);
    }

    private ServiceTypeApplicationService service() {
        return new ServiceTypeApplicationService(serviceTypeRepository, serviceRequestRepository, resourceRepository);
    }
}
