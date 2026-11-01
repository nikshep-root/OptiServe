package com.minor_project.optiserve_backend.operations.scheduler.api;

import com.minor_project.optiserve_backend.operations.scheduler.application.SchedulingDecisionQueryService;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingDecisionResult;
import com.minor_project.optiserve_backend.operations.scheduler.domain.SchedulingTrigger;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/scheduling/decisions")
public class SchedulingDecisionController {

    private final SchedulingDecisionQueryService queryService;

    public SchedulingDecisionController(SchedulingDecisionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public SchedulingDecisionPageResponse search(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) SchedulingTrigger trigger,
            @RequestParam(required = false) SchedulingDecisionResult outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return queryService.search(from, to, trigger, outcome, page, size);
    }

    @GetMapping("/{decisionId}")
    public SchedulingDecisionDetailResponse findById(@PathVariable UUID decisionId) {
        return queryService.findById(decisionId);
    }
}
