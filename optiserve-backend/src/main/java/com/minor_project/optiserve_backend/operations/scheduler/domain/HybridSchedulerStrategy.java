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
import java.util.List;
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
        return rankCandidates(candidates).stream().findFirst();
    }

    @Override
    public List<QueueEntry> rankCandidates(Collection<QueueEntry> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Instant now = Instant.now(clock);
        return candidates.stream()
                .filter(HybridSchedulerStrategy::isQueuedCandidate)
                .sorted(candidateComparator(now).reversed())
                .toList();
    }

    @Override
    public List<SchedulerCandidateEvaluation> evaluateCandidates(Collection<QueueEntry> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Instant now = Instant.now(clock);
        List<QueueEntry> ranked = candidates.stream()
                .filter(HybridSchedulerStrategy::isQueuedCandidate)
                .sorted(candidateComparator(now).reversed())
                .toList();
        return java.util.stream.IntStream.range(0, ranked.size())
                .mapToObj(index -> evaluation(ranked.get(index), index, now))
                .toList();
    }

    SchedulerScore score(QueueEntry entry) {
        return score(entry, Instant.now(clock));
    }

    private SchedulerScore score(QueueEntry entry, Instant now) {
        Objects.requireNonNull(entry, "entry must not be null");
        PriorityClass priority = entry.getServiceRequest().getPriorityClass();
        if (priority == PriorityClass.CRITICAL) {
            return new SchedulerScore(true, 0);
        }
        long score = basePriority(priority) + agingPoints(entry.getQueuedAt(), now);
        score += appointmentBonus(entry, now);
        score += durationBonus(entry.getServiceStage());
        return new SchedulerScore(false, score);
    }

    private SchedulerCandidateEvaluation evaluation(QueueEntry entry, int order, Instant now) {
        PriorityClass priority = entry.getServiceRequest().getPriorityClass();
        boolean critical = priority == PriorityClass.CRITICAL;
        long base = critical ? 0 : basePriority(priority);
        long aging = critical ? 0 : agingPoints(entry.getQueuedAt(), now);
        long appointment = critical ? 0 : appointmentBonus(entry, now);
        long duration = critical ? 0 : durationBonus(entry.getServiceStage());
        long finalScore = critical ? 0 : base + aging + appointment + duration;
        Instant appointmentAt = entry.getServiceRequest().getAppointmentAt();
        boolean appointmentDue = priority == PriorityClass.APPOINTMENT
                && appointmentAt != null && !appointmentAt.isAfter(now);
        return new SchedulerCandidateEvaluation(
                entry,
                order,
                entry.getStatus(),
                entry.getServiceStage().getStatus(),
                priority,
                entry.getQueuedAt(),
                Duration.ofSeconds(Math.max(0, Duration.between(entry.getQueuedAt(), now).getSeconds())),
                appointmentAt,
                appointmentDue,
                entry.getServiceStage().getPredictedServiceDuration(),
                entry.getServiceStage().getPredictionSource(),
                critical,
                base,
                aging,
                appointment,
                duration,
                finalScore,
                SchedulingFeasibilityStatus.NOT_EVALUATED,
                null,
                null,
                SchedulingCandidateOutcome.NOT_EVALUATED,
                SchedulingCandidateReason.OTHER_VALIDATED_REASON);
    }

    private Comparator<QueueEntry> candidateComparator(Instant now) {
        return Comparator.comparing((QueueEntry entry) -> score(entry, now).critical())
                .thenComparingLong(entry -> score(entry, now).value())
                .thenComparing(QueueEntry::getQueuedAt, Comparator.reverseOrder())
                .thenComparing(entry -> entry.getServiceRequest().getRequestedAt(), Comparator.reverseOrder())
                .thenComparing(QueueEntry::getId, Comparator.reverseOrder());
    }

    private long agingPoints(Instant queuedAt, Instant now) {
        long waitingMinutes = Math.max(0, Duration.between(queuedAt, now).toMinutes());
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
