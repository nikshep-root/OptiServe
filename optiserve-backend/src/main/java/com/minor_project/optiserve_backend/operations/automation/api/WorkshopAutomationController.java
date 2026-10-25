package com.minor_project.optiserve_backend.operations.automation.api;

import com.minor_project.optiserve_backend.operations.automation.application.WorkshopAutomationApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/workshop-automation")
public class WorkshopAutomationController {

    private final WorkshopAutomationApplicationService applicationService;

    public WorkshopAutomationController(WorkshopAutomationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public WorkshopAutomationModeResponse getMode() {
        return applicationService.getMode();
    }

    @PutMapping
    public WorkshopAutomationModeResponse setMode(@Valid @RequestBody WorkshopAutomationModeRequest request) {
        return applicationService.setMode(request.mode());
    }
}
