package com.minor_project.optiserve_backend.operations.servicetype.api;

import com.minor_project.optiserve_backend.operations.servicetype.application.ServiceTypeApplicationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-types")
public class ServiceTypeController {

    private final ServiceTypeApplicationService serviceTypeApplicationService;

    public ServiceTypeController(ServiceTypeApplicationService serviceTypeApplicationService) {
        this.serviceTypeApplicationService = serviceTypeApplicationService;
    }

    @PostMapping
    public ResponseEntity<ServiceTypeResponse> create(@Valid @RequestBody CreateServiceTypeRequest request) {
        ServiceTypeResponse response = serviceTypeApplicationService.create(request);
        return ResponseEntity.created(URI.create("/api/service-types/" + response.id())).body(response);
    }

    @GetMapping
    public List<ServiceTypeResponse> findAll() {
        return serviceTypeApplicationService.findAll();
    }

    @GetMapping("/{id}")
    public ServiceTypeResponse findById(@PathVariable UUID id) {
        return serviceTypeApplicationService.findById(id);
    }

    @PatchMapping("/{id}")
    public ServiceTypeResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceTypeRequest request) {
        return serviceTypeApplicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        serviceTypeApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
