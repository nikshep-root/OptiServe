package com.minor_project.optiserve_backend.operations.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.operations.domain.Resource;
import com.minor_project.optiserve_backend.operations.domain.ResourceStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.persistence.ResourceRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.resource.api.CreateResourceRequest;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceRequest;
import com.minor_project.optiserve_backend.operations.resource.api.UpdateResourceStatusRequest;
import com.minor_project.optiserve_backend.operations.resource.application.ResourceApplicationService;
import com.minor_project.optiserve_backend.operations.servicetype.api.CreateServiceTypeRequest;
import com.minor_project.optiserve_backend.operations.servicetype.api.UpdateServiceTypeRequest;
import com.minor_project.optiserve_backend.operations.servicetype.application.ServiceTypeApplicationService;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ApplicationCacheIntegrationTests {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ServiceTypeApplicationService serviceTypeService;

    @Autowired
    private ResourceApplicationService resourceService;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @AfterEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void cachesServiceTypeByIdAndListAndRefreshesAfterSuccessfulUpdate() {
        ServiceType serviceType = saveServiceType("Oil change");

        var cachedById = serviceTypeService.findById(serviceType.getId());
        assertThat(cachedById.name()).isEqualTo("Oil change");
        assertThat(serviceTypeService.findById(serviceType.getId())).isSameAs(cachedById);
        var cachedList = serviceTypeService.findAll();
        assertThat(cachedList).extracting(item -> item.name()).contains("Oil change");
        assertThat(serviceTypeService.findAll()).isSameAs(cachedList);
        assertThat(cache("serviceTypesById").get(serviceType.getId())).isNotNull();
        assertThat(cache("serviceTypeLists").get("all")).isNotNull();

        serviceTypeService.update(serviceType.getId(),
                new UpdateServiceTypeRequest("Oil and filter", null, 1200L, null));

        assertThat(cache("serviceTypesById").get(serviceType.getId())).isNull();
        assertThat(cache("serviceTypeLists").get("all")).isNull();
        assertThat(serviceTypeService.findById(serviceType.getId()).name()).isEqualTo("Oil and filter");
        assertThat(serviceTypeService.findAll()).extracting("name").contains("Oil and filter");
    }

    @Test
    void createAndDeleteInvalidateCachedServiceTypeReads() {
        ServiceType existing = saveServiceType("Existing service");
        serviceTypeService.findAll();
        serviceTypeService.findById(existing.getId());

        var created = serviceTypeService.create(new CreateServiceTypeRequest("New service", null, 1800L, true));

        assertThat(cache("serviceTypesById").get(existing.getId())).isNull();
        assertThat(cache("serviceTypeLists").get("all")).isNull();
        assertThat(serviceTypeService.findAll()).extracting(item -> item.name()).contains("New service");
        serviceTypeService.findById(created.id());

        serviceTypeService.delete(created.id());

        assertThat(cache("serviceTypesById").get(created.id())).isNull();
        assertThat(cache("serviceTypeLists").get("all")).isNull();
        assertThatThrownBy(() -> serviceTypeService.findById(created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void compatibilityReadsRefreshAfterChangesAndFailedDuplicateDoesNotChangeDatabaseOrCache() {
        ServiceType serviceType = saveServiceType("Inspection");
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Bay A", Set.of()));

        assertThat(resourceService.findCompatibleServiceTypes(resource.getId())).isEmpty();
        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId())).isNotNull();

        resourceService.addCompatibility(resource.getId(), serviceType.getId());

        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId())).isNull();
        assertThat(resourceService.findCompatibleServiceTypes(resource.getId()))
                .extracting("id").containsExactly(serviceType.getId());

        Object cachedCompatibility = cache("resourceCompatibleServiceTypes").get(resource.getId()).get();
        assertThatThrownBy(() -> resourceService.addCompatibility(resource.getId(), serviceType.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId()).get()).isSameAs(cachedCompatibility);
        assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getCompatibleServiceTypes())
                .extracting(ServiceType::getId).containsExactly(serviceType.getId());

        resourceService.removeCompatibility(resource.getId(), serviceType.getId());

        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId())).isNull();
        assertThat(resourceService.findCompatibleServiceTypes(resource.getId())).isEmpty();
    }

    @Test
    void successfulResourceMutationsInvalidateCompatibilityAndResourceStatusReadsStayLive() {
        ServiceType serviceType = saveServiceType("Brake service");
        Resource resource = resourceRepository.saveAndFlush(Resource.create("Bay A", Set.of(serviceType)));
        resourceService.findCompatibleServiceTypes(resource.getId());

        resourceService.update(resource.getId(), new UpdateResourceRequest("Bay B"));
        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId())).isNull();
        resourceService.findCompatibleServiceTypes(resource.getId());

        resourceService.changeStatus(resource.getId(), new UpdateResourceStatusRequest(ResourceStatus.OFFLINE));
        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId())).isNull();
        assertThat(resourceService.findById(resource.getId()).status()).isEqualTo(ResourceStatus.OFFLINE);
        assertThat(resourceService.findAll()).anyMatch(item -> item.id().equals(resource.getId())
                && item.status() == ResourceStatus.OFFLINE);

        resourceService.findCompatibleServiceTypes(resource.getId());
        resourceService.delete(resource.getId());
        assertThat(cache("resourceCompatibleServiceTypes").get(resource.getId())).isNull();
    }

    @Test
    void failedServiceTypeCreateLeavesDatabaseAndCachedListUnchanged() {
        saveServiceType("Registered service");
        var cachedList = serviceTypeService.findAll();
        long countBefore = serviceTypeRepository.count();

        assertThatThrownBy(() -> serviceTypeService.create(
                new CreateServiceTypeRequest("Registered service", null, 900L, true)))
                .isInstanceOf(ConflictException.class);

        assertThat(serviceTypeRepository.count()).isEqualTo(countBefore);
        assertThat(cache("serviceTypeLists").get("all").get()).isSameAs(cachedList);
    }

    private ServiceType saveServiceType(String name) {
        return serviceTypeRepository.saveAndFlush(
                ServiceType.create(name, null, Duration.ofMinutes(15)));
    }

    private Cache cache(String name) {
        return cacheManager.getCache(name);
    }
}
