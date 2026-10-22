package com.minor_project.optiserve_backend.operations.assignment.api;

import com.minor_project.optiserve_backend.operations.assignment.application.AssignmentApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentApplicationService assignmentApplicationService;

    public AssignmentController(AssignmentApplicationService assignmentApplicationService) {
        this.assignmentApplicationService = assignmentApplicationService;
    }

    @GetMapping
    public List<AssignmentSummaryResponse> findAssignments(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return admin
                ? assignmentApplicationService.findAllAssignments()
                : assignmentApplicationService.findAssignmentsForCustomer(authentication.getName());
    }

    @PostMapping("/next")
    public AssignmentNextResponse assignNext() {
        return assignmentApplicationService.assignNext();
    }

    @PostMapping("/{assignmentId}/start")
    public AssignmentExecutionResponse start(@PathVariable java.util.UUID assignmentId) {
        return assignmentApplicationService.start(assignmentId);
    }

    @PostMapping("/{assignmentId}/complete")
    public AssignmentExecutionResponse complete(@PathVariable java.util.UUID assignmentId,
            @Valid @RequestBody(required = false) CompleteAssignmentRequest request) {
        return assignmentApplicationService.complete(assignmentId, request == null ? null : request.actualDurationMinutes());
    }
}
