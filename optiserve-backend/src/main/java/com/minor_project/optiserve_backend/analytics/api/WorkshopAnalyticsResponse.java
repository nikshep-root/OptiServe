package com.minor_project.optiserve_backend.analytics.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WorkshopAnalyticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        HistoricalMetrics historical,
        LiveMetrics current,
        List<CompletionTrendPoint> completionTrend) {

    public record HistoricalMetrics(
            long requestsCreated,
            long requestsCompleted,
            BigDecimal averageActualServiceDurationMinutes,
            BigDecimal meanAbsolutePredictionErrorMinutes,
            long predictionErrorSampleCount) {
    }

    public record LiveMetrics(
            long waitingQueueSize,
            long activeAssignments,
            long availableResources,
            long busyResources,
            long offlineResources) {
    }

    public record CompletionTrendPoint(LocalDate date, long completedRequests) {
    }
}
