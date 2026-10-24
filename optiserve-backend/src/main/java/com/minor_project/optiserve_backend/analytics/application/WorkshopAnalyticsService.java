package com.minor_project.optiserve_backend.analytics.application;

import com.minor_project.optiserve_backend.analytics.api.WorkshopAnalyticsResponse;
import com.minor_project.optiserve_backend.analytics.api.WorkshopAnalyticsResponse.CompletionTrendPoint;
import com.minor_project.optiserve_backend.analytics.api.WorkshopAnalyticsResponse.HistoricalMetrics;
import com.minor_project.optiserve_backend.analytics.api.WorkshopAnalyticsResponse.LiveMetrics;
import com.minor_project.optiserve_backend.analytics.persistence.WorkshopAnalyticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkshopAnalyticsService {

    private final WorkshopAnalyticsRepository repository;

    public WorkshopAnalyticsService(WorkshopAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkshopAnalyticsResponse overview(LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var predictionError = repository.predictionError(from, to);
        var counts = repository.liveCounts();
        List<CompletionTrendPoint> trend = new ArrayList<>();
        var dailyCounts = repository.completionTrend(from, to);
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            trend.add(new CompletionTrendPoint(date, dailyCounts.getOrDefault(date, 0L)));
        }
        return new WorkshopAnalyticsResponse(fromDate, toDate,
                new HistoricalMetrics(repository.countRequestsCreated(from, to),
                        repository.countRequestsCompleted(from, to),
                        repository.averageActualDurationMinutes(from, to),
                        predictionError.meanAbsoluteErrorMinutes(), predictionError.sampleCount()),
                new LiveMetrics(counts.waitingQueueSize(), counts.activeAssignments(), counts.availableResources(),
                        counts.busyResources(), counts.offlineResources()),
                List.copyOf(trend));
    }
}
