package com.minor_project.optiserve_backend.operations.assignment.api;

import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentApplicationService assignmentApplicationService;

    public AssignmentController(AssignmentApplicationService assignmentApplicationService) {
        this.assignmentApplicationService = assignmentApplicationService;
    }

    @PostMapping("/next")
    public AssignmentNextResponse assignNext() {
        return assignmentApplicationService.assignNext();
    }
}
