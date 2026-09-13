package com.minor_project.optiserve_backend.operations.servicerequest.api;

import com.minor_project.optiserve_backend.operations.servicerequest.application.ServiceRequestApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-requests")
public class ServiceRequestController {

    private final ServiceRequestApplicationService serviceRequestApplicationService;

    public ServiceRequestController(ServiceRequestApplicationService serviceRequestApplicationService) {
        this.serviceRequestApplicationService = serviceRequestApplicationService;
    }

    @PostMapping
    public ResponseEntity<ServiceRequestResponse> create(@Valid @RequestBody CreateServiceRequestRequest request) {
        ServiceRequestResponse response = serviceRequestApplicationService.create(request);
        return ResponseEntity.created(URI.create("/api/service-requests/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ServiceRequestResponse findById(@PathVariable UUID id) {
        return serviceRequestApplicationService.findById(id);
    }

    @GetMapping
    public List<ServiceRequestResponse> findAll() {
        return serviceRequestApplicationService.findAll();
    }
}
