package com.minor_project.optiserve_backend.operations.api;

import com.minor_project.optiserve_backend.operations.api.dto.QuickIntakeResponse;
import com.minor_project.optiserve_backend.operations.api.dto.QuickVehicleIntakeRequest;
import com.minor_project.optiserve_backend.operations.application.QuickIntakeApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/intake")
public class AdminVehicleIntakeController {

    private final QuickIntakeApplicationService quickIntakeService;

    public AdminVehicleIntakeController(QuickIntakeApplicationService quickIntakeService) {
        this.quickIntakeService = quickIntakeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SERVICE_ADVISOR')")
    public QuickIntakeResponse processIntake(@Valid @RequestBody QuickVehicleIntakeRequest request) {
        return quickIntakeService.processQuickIntake(request);
    }
}
