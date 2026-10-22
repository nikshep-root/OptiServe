package com.minor_project.optiserve_backend.operations.vehicle.api;

import com.minor_project.optiserve_backend.operations.vehicle.application.VehicleApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleApplicationService vehicleApplicationService;

    public VehicleController(VehicleApplicationService vehicleApplicationService) {
        this.vehicleApplicationService = vehicleApplicationService;
    }

    @GetMapping
    public List<VehicleResponse> findMine(Authentication authentication) {
        return vehicleApplicationService.findMine(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(
            Authentication authentication, @Valid @RequestBody VehicleRequest request) {
        VehicleResponse response = vehicleApplicationService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/vehicles/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public VehicleResponse findMine(Authentication authentication, @PathVariable UUID id) {
        return vehicleApplicationService.findMine(authentication.getName(), id);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return vehicleApplicationService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        vehicleApplicationService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
