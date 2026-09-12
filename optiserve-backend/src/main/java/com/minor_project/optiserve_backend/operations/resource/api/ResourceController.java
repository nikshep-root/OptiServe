package com.minor_project.optiserve_backend.operations.resource.api;

import com.minor_project.optiserve_backend.operations.resource.application.ResourceApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceApplicationService resourceApplicationService;

    public ResourceController(ResourceApplicationService resourceApplicationService) {
        this.resourceApplicationService = resourceApplicationService;
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse response = resourceApplicationService.create(request);
        return ResponseEntity.created(URI.create("/api/resources/" + response.id())).body(response);
    }

    @GetMapping
    public List<ResourceResponse> findAll() {
        return resourceApplicationService.findAll();
    }

    @GetMapping("/{id}")
    public ResourceResponse findById(@PathVariable UUID id) {
        return resourceApplicationService.findById(id);
    }

    @PatchMapping("/{id}")
    public ResourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateResourceRequest request) {
        return resourceApplicationService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ResourceResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateResourceStatusRequest request) {
        return resourceApplicationService.changeStatus(id, request);
    }

    @PutMapping("/{resourceId}/service-types/{serviceTypeId}")
    public ResourceResponse addCompatibility(@PathVariable UUID resourceId, @PathVariable UUID serviceTypeId) {
        return resourceApplicationService.addCompatibility(resourceId, serviceTypeId);
    }

    @DeleteMapping("/{resourceId}/service-types/{serviceTypeId}")
    public ResponseEntity<Void> removeCompatibility(@PathVariable UUID resourceId, @PathVariable UUID serviceTypeId) {
        resourceApplicationService.removeCompatibility(resourceId, serviceTypeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{resourceId}/service-types")
    public List<CompatibleServiceTypeResponse> findCompatibleServiceTypes(@PathVariable UUID resourceId) {
        return resourceApplicationService.findCompatibleServiceTypes(resourceId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        resourceApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
