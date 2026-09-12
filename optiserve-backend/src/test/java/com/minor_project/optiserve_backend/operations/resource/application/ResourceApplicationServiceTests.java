package com.minor_project.optiserve_backend.operations.resource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.AssignmentRepository;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.resource.api.CreateResourceRequest;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceRequest;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceStatusRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceApplicationServiceTests {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Test
    void createsAResourceWithNormalizedName() {
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().create(new CreateResourceRequest("  Counter A  "));

        ArgumentCaptor<Resource> saved = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(saved.capture());
        assertThat(saved.getValue())
                .extracting(Resource::getName, Resource::getStatus)
                .containsExactly("Counter A", ResourceStatus.AVAILABLE);
        assertThat(response.compatibleServiceTypes()).isEmpty();
    }

    @Test
    void rejectsDuplicateResourceNamesIgnoringCase() {
        when(resourceRepository.existsByNameIgnoreCase("Counter A")).thenReturn(true);

        assertThatThrownBy(() -> service().create(new CreateResourceRequest("Counter A")))
                .isInstanceOf(ConflictException.class);

        verify(resourceRepository, never()).save(any());
    }

    @Test
    void updatesResourceNameAndStatusWhileRejectingBusyFromOffline() {
        UUID id = UUID.randomUUID();
        Resource resource = Resource.create("Counter A", Set.of());
        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));

        var updated = service().update(id, new UpdateResourceRequest("Counter B"));
        var offline = service().changeStatus(id, new UpdateResourceStatusRequest(ResourceStatus.OFFLINE));

        assertThat(updated.name()).isEqualTo("Counter B");
        assertThat(offline.status()).isEqualTo(ResourceStatus.OFFLINE);
        assertThatThrownBy(() -> service().changeStatus(id, new UpdateResourceStatusRequest(ResourceStatus.BUSY)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsMissingResourceAndServiceType() {
        UUID resourceId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findById(resourceId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().addCompatibility(resourceId, serviceTypeId))
                .isInstanceOf(ResourceNotFoundException.class);

        Resource resource = Resource.create("Counter A", Set.of());
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(serviceTypeRepository.findById(serviceTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addCompatibility(resourceId, serviceTypeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addsRejectsDuplicatesAndRemovesCompatibility() {
        UUID resourceId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        ServiceType serviceType = ServiceType.create("Registration", null, Duration.ofMinutes(15));
        Resource resource = Resource.create("Counter A", Set.of());
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(serviceTypeRepository.findById(serviceTypeId)).thenReturn(Optional.of(serviceType));

        service().addCompatibility(resourceId, serviceTypeId);
        assertThat(resource.supports(serviceType)).isTrue();

        assertThatThrownBy(() -> service().addCompatibility(resourceId, serviceTypeId))
                .isInstanceOf(ConflictException.class);

        service().removeCompatibility(resourceId, serviceTypeId);
        assertThat(resource.supports(serviceType)).isFalse();
        assertThatThrownBy(() -> service().removeCompatibility(resourceId, serviceTypeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesUnreferencedResourcesAndDeactivatesReferencedResources() {
        UUID deletableId = UUID.randomUUID();
        Resource deletable = Resource.create("Counter A", Set.of());
        when(resourceRepository.findById(deletableId)).thenReturn(Optional.of(deletable));
        when(assignmentRepository.existsByResourceId(deletableId)).thenReturn(false);

        service().delete(deletableId);

        verify(resourceRepository).delete(deletable);

        UUID referencedId = UUID.randomUUID();
        Resource referenced = Resource.create("Counter B", Set.of());
        when(resourceRepository.findById(referencedId)).thenReturn(Optional.of(referenced));
        when(assignmentRepository.existsByResourceId(referencedId)).thenReturn(true);

        service().delete(referencedId);

        assertThat(referenced.getStatus()).isEqualTo(ResourceStatus.OFFLINE);
        verify(resourceRepository, never()).delete(referenced);
    }

    private ResourceApplicationService service() {
        return new ResourceApplicationService(resourceRepository, serviceTypeRepository, assignmentRepository);
    }
}
