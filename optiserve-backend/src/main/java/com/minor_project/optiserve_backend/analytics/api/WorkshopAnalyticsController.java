package com.minor_project.optiserve_backend.analytics.api;

import com.minor_project.optiserve_backend.analytics.application.WorkshopAnalyticsService;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
public class WorkshopAnalyticsController {

    private final WorkshopAnalyticsService analyticsService;

    public WorkshopAnalyticsController(WorkshopAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public WorkshopAnalyticsResponse overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        if (fromDate.isAfter(toDate) || ChronoUnit.DAYS.between(fromDate, toDate) > 365
                || toDate.equals(LocalDate.MAX)) {
            throw new ConstraintViolationException("Date range must be ordered and no longer than 366 days.", Set.of());
        }
        return analyticsService.overview(fromDate, toDate);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ConstraintViolationException("Both dates must use the ISO format YYYY-MM-DD.", Set.of());
        }
    }
}
