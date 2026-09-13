package com.minor_project.optiserve_backend.operations.scheduler.domain;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Hybrid, read-only queue selector. CRITICAL requests are always considered before every
 * non-critical request. Within each group, an explainable score combines base priority, aging,
 * a due-appointment bonus, and a bounded short-job-first adjustment.
 */
public final class HybridSchedulerStrategy implements SchedulerStrategy {

    static final long URGENT_BASE_SCORE = 300;
    static final long APPOINTMENT_BASE_SCORE = 200;
    static final long NORMAL_BASE_SCORE = 100;
    static final long AGING_POINTS_PER_MINUTE = 1;
    static final long DUE_APPOINTMENT_BONUS = 25;
    static final long DURATION_REFERENCE_MINUTES = 60;
    static final long MAX_DURATION_BONUS = 30;

    private final Clock clock;

    public HybridSchedulerStrategy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<QueueEntry> selectNext(Collection<QueueEntry> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        return candidates.stream()
                .filter(HybridSchedulerStrategy::isQueuedCandidate)
                .max(candidateComparator());
    }

    SchedulerScore score(QueueEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        PriorityClass priority = entry.getServiceRequest().getPriorityClass();
        if (priority == PriorityClass.CRITICAL) {
            return new SchedulerScore(true, 0);
        }
        long score = basePriority(priority) + agingPoints(entry.getQueuedAt());
        score += appointmentBonus(entry, Instant.now(clock));
        score += durationBonus(entry.getServiceStage());
        return new SchedulerScore(false, score);
    }

    private Comparator<QueueEntry> candidateComparator() {
        return Comparator.comparing((QueueEntry entry) -> score(entry).critical())
                .thenComparingLong(entry -> score(entry).value())
                .thenComparing(QueueEntry::getQueuedAt, Comparator.reverseOrder())
                .thenComparing(entry -> entry.getServiceRequest().getRequestedAt(), Comparator.reverseOrder())
                .thenComparing(QueueEntry::getId, Comparator.reverseOrder());
    }

    private long agingPoints(Instant queuedAt) {
        long waitingMinutes = Math.max(0, Duration.between(queuedAt, Instant.now(clock)).toMinutes());
        return Math.multiplyExact(waitingMinutes, AGING_POINTS_PER_MINUTE);
    }

    private long appointmentBonus(QueueEntry entry, Instant now) {
        Instant appointmentAt = entry.getServiceRequest().getAppointmentAt();
        return entry.getServiceRequest().getPriorityClass() == PriorityClass.APPOINTMENT
                        && appointmentAt != null
                        && !appointmentAt.isAfter(now)
                ? DUE_APPOINTMENT_BONUS
                : 0;
    }

    private long durationBonus(ServiceStage stage) {
        Duration duration = stage.getPredictedServiceDuration();
        if (duration == null) {
            return 0;
        }
        long durationMinutes = Math.max(0, duration.toMinutes());
        return Math.min(MAX_DURATION_BONUS, Math.max(0, DURATION_REFERENCE_MINUTES - durationMinutes));
    }

    private static boolean isQueuedCandidate(QueueEntry entry) {
        return entry != null
                && entry.getStatus() == QueueEntryStatus.WAITING
                && entry.getServiceStage().getStatus() == ServiceStageStatus.QUEUED;
    }

    private static long basePriority(PriorityClass priority) {
        return switch (priority) {
            case URGENT -> URGENT_BASE_SCORE;
            case APPOINTMENT -> APPOINTMENT_BASE_SCORE;
            case NORMAL -> NORMAL_BASE_SCORE;
            case CRITICAL -> 0;
        };
    }
}
