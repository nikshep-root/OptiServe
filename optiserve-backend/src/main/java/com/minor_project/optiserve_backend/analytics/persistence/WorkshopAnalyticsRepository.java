package com.minor_project.optiserve_backend.analytics.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class WorkshopAnalyticsRepository {

    private static final String COMPLETED_REQUEST_PREDICATE = """
            from assignments assignment
            join service_workflows workflow on workflow.service_request_id = assignment.service_request_id
            join service_stages stage on stage.id = assignment.service_stage_id
            where assignment.status = 'COMPLETED'
              and workflow.status = 'COMPLETED'
              and not exists (
                  select 1 from service_stages later_stage
                  where later_stage.workflow_id = stage.workflow_id
                    and later_stage.sequence_number > stage.sequence_number
              )
            """;

    private final JdbcTemplate jdbcTemplate;

    public WorkshopAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countRequestsCreated(Instant fromInclusive, Instant toExclusive) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from service_requests where requested_at >= ? and requested_at < ?",
                Long.class, parameter(fromInclusive), parameter(toExclusive));
        return count == null ? 0 : count;
    }

    public long countRequestsCompleted(Instant fromInclusive, Instant toExclusive) {
        String sql = "select count(*) " + COMPLETED_REQUEST_PREDICATE
                + " and assignment.completed_at >= ? and assignment.completed_at < ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class,
                parameter(fromInclusive), parameter(toExclusive));
        return count == null ? 0 : count;
    }

    public BigDecimal averageActualDurationMinutes(Instant fromInclusive, Instant toExclusive) {
        String sql = "select avg(assignment.actual_service_duration) / 60000000000.0 from assignments assignment"
                + " where assignment.status = 'COMPLETED' and assignment.completed_at >= ? and assignment.completed_at < ?"
                + " and assignment.actual_service_duration is not null";
        return jdbcTemplate.queryForObject(sql, BigDecimal.class,
                parameter(fromInclusive), parameter(toExclusive));
    }

    public PredictionError predictionError(Instant fromInclusive, Instant toExclusive) {
        String sql = "select count(*), avg(abs(assignment.predicted_service_duration"
                + " - assignment.actual_service_duration)) / 60000000000.0 from assignments assignment"
                + " where assignment.status = 'COMPLETED' and assignment.completed_at >= ? and assignment.completed_at < ?"
                + " and assignment.predicted_service_duration is not null"
                + " and assignment.actual_service_duration is not null";
        return jdbcTemplate.queryForObject(sql, (result, row) -> new PredictionError(
                result.getLong(1), result.getBigDecimal(2)),
                parameter(fromInclusive), parameter(toExclusive));
    }

    public Map<LocalDate, Long> completionTrend(Instant fromInclusive, Instant toExclusive) {
        String sql = "select (assignment.completed_at at time zone 'UTC')::date as completed_day, count(*) "
                + COMPLETED_REQUEST_PREDICATE
                + " and assignment.completed_at >= ? and assignment.completed_at < ?"
                + " group by completed_day order by completed_day";
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (RowCallbackHandler) result ->
                        counts.put(result.getDate("completed_day").toLocalDate(), result.getLong(2)),
                parameter(fromInclusive), parameter(toExclusive));
        return counts;
    }

    public LiveCounts liveCounts() {
        Long waiting = jdbcTemplate.queryForObject(
                "select count(*) from queue_entries where status = 'WAITING'", Long.class);
        Long assignments = jdbcTemplate.queryForObject(
                "select count(*) from assignments where status in ('ASSIGNED', 'IN_PROGRESS')", Long.class);
        Map<String, Long> resourceCounts = new LinkedHashMap<>();
        jdbcTemplate.query("select status, count(*) from resources group by status",
                (RowCallbackHandler) result -> resourceCounts.put(result.getString(1), result.getLong(2)));
        return new LiveCounts(value(waiting), value(assignments), resourceCounts.getOrDefault("AVAILABLE", 0L),
                resourceCounts.getOrDefault("BUSY", 0L), resourceCounts.getOrDefault("OFFLINE", 0L));
    }

    public record PredictionError(long sampleCount, BigDecimal meanAbsoluteErrorMinutes) {
    }

    public record LiveCounts(long waitingQueueSize, long activeAssignments, long availableResources,
            long busyResources, long offlineResources) {
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static OffsetDateTime parameter(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
