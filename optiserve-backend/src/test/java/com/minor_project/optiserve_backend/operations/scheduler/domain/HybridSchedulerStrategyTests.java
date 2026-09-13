package com.minor_project.optiserve_backend.operations.scheduler.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.QueueEntryStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceStageStatus;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.ServiceWorkflow;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HybridSchedulerStrategyTests {

    private static final Instant NOW = Instant.parse("2026-09-14T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void criticalBeatsNormalAndUrgentRegardlessOfOtherAdjustments() {
        QueueEntry critical = queued(PriorityClass.CRITICAL, NOW.minusSeconds(60), NOW.minusSeconds(60), Duration.ofMinutes(60), 1);
        QueueEntry normal = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(2)), Duration.ofMinutes(1), 2);
        QueueEntry urgent = queued(PriorityClass.URGENT, NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(2)), Duration.ofMinutes(1), 3);

        assertThat(strategy().selectNext(List.of(normal, urgent, critical))).containsSame(critical);
    }

    @Test
    void urgentBeatsNormalWhenOtherFactorsAreEqual() {
        QueueEntry urgent = queued(PriorityClass.URGENT, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), null, 1);
        QueueEntry normal = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), null, 2);

        assertThat(strategy().selectNext(List.of(normal, urgent))).containsSame(urgent);
    }

    @Test
    void agingIncreasesEffectivePriorityAndLetsLongWaitingNormalOvertakeNewerNormal() {
        QueueEntry waiting = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(90)), NOW.minus(Duration.ofHours(2)), null, 1);
        QueueEntry newer = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofHours(2)), null, 2);
        HybridSchedulerStrategy laterStrategy = new HybridSchedulerStrategy(Clock.fixed(NOW.plus(Duration.ofMinutes(30)), ZoneOffset.UTC));

        assertThat(laterStrategy.score(waiting).value()).isGreaterThan(strategy().score(waiting).value());
        assertThat(strategy().selectNext(List.of(newer, waiting))).containsSame(waiting);
    }

    @Test
    void shorterPredictedDurationGetsBoundedAdvantageAndMissingDurationIsSafe() {
        QueueEntry shortJob = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(10), 1);
        QueueEntry longJob = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(60), 2);
        QueueEntry missingDuration = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), null, 3);

        assertThat(strategy().selectNext(List.of(longJob, shortJob))).containsSame(shortJob);
        assertThat(strategy().selectNext(List.of(missingDuration))).containsSame(missingDuration);
    }

    @Test
    void dueAppointmentReceivesConsiderationWithoutOverridingCriticalProtection() {
        QueueEntry dueAppointment = queued(PriorityClass.APPOINTMENT, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), null, 1, NOW.minus(Duration.ofMinutes(1)));
        QueueEntry futureAppointment = queued(PriorityClass.APPOINTMENT, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), null, 2, NOW.plus(Duration.ofHours(1)));
        QueueEntry critical = queued(PriorityClass.CRITICAL, NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(60), 3);

        assertThat(strategy().score(dueAppointment).value()).isGreaterThan(strategy().score(futureAppointment).value());
        assertThat(strategy().selectNext(List.of(dueAppointment, critical))).containsSame(critical);
    }

    @Test
    void equalScoresUseQueueTimeThenRequestCreationTimeThenUuid() {
        QueueEntry earlierQueueEntry = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(11)), NOW.minus(Duration.ofHours(1)), null, 2);
        QueueEntry laterQueueEntry = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(2)), null, 1);
        assertThat(strategy().selectNext(List.of(laterQueueEntry, earlierQueueEntry))).containsSame(earlierQueueEntry);

        Instant sameQueueTime = NOW.minus(Duration.ofMinutes(10));
        QueueEntry earlierRequest = queued(PriorityClass.NORMAL, sameQueueTime, NOW.minus(Duration.ofHours(2)), null, 3);
        QueueEntry laterRequest = queued(PriorityClass.NORMAL, sameQueueTime, NOW.minus(Duration.ofHours(1)), null, 2);
        assertThat(strategy().selectNext(List.of(laterRequest, earlierRequest))).containsSame(earlierRequest);

        QueueEntry lowerUuid = queued(PriorityClass.NORMAL, sameQueueTime, NOW.minus(Duration.ofHours(1)), null, 1);
        QueueEntry higherUuid = queued(PriorityClass.NORMAL, sameQueueTime, NOW.minus(Duration.ofHours(1)), null, 2);
        assertThat(strategy().selectNext(List.of(lowerUuid, higherUuid))).containsSame(lowerUuid);
    }

    @Test
    void onlyCurrentQueuedEntriesAreConsideredAndSelectionDoesNotMutateState() {
        QueueEntry active = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofHours(1)), null, 1);
        QueueEntry removed = queued(PriorityClass.CRITICAL, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)), null, 2);
        removed.remove();
        QueueEntry selected = strategy().selectNext(List.of(removed, active)).orElseThrow();

        assertThat(selected).isSameAs(active);
        assertThat(active.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(active.getServiceStage().getStatus()).isEqualTo(ServiceStageStatus.QUEUED);
        assertThat(active.getQueuedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void emptyCandidatesAreHandledAndFixedClockMakesSelectionDeterministic() {
        assertThat(strategy().selectNext(List.of())).isEmpty();
        QueueEntry entry = queued(PriorityClass.NORMAL, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(1)), null, 1);

        assertThat(strategy().score(entry).value()).isEqualTo(NORMAL_SCORE_AFTER_TEN_MINUTES);
    }

    private static final long NORMAL_SCORE_AFTER_TEN_MINUTES = 110;

    private HybridSchedulerStrategy strategy() {
        return new HybridSchedulerStrategy(CLOCK);
    }

    private QueueEntry queued(
            PriorityClass priority,
            Instant queuedAt,
            Instant requestedAt,
            Duration predictedDuration,
            long idSuffix) {
        return queued(priority, queuedAt, requestedAt, predictedDuration, idSuffix, null);
    }

    private QueueEntry queued(
            PriorityClass priority,
            Instant queuedAt,
            Instant requestedAt,
            Duration predictedDuration,
            long idSuffix,
            Instant appointmentAt) {
        ServiceType serviceType = ServiceType.create("Inspection " + idSuffix, null, Duration.ofMinutes(30));
        Vehicle vehicle = Vehicle.create(UUID.randomUUID(), "KA01AB" + idSuffix, "Toyota", "Camry", 2024);
        ServiceRequest request = ServiceRequest.create(vehicle, serviceType, priority, appointmentAt);
        setField(request, "requestedAt", requestedAt);
        ServiceWorkflow workflow = ServiceWorkflow.create(request);
        ServiceStage stage = workflow.addStage(serviceType, predictedDuration);
        QueueEntry entry = QueueEntry.enter(stage, queuedAt);
        setField(entry, "id", new UUID(0, idSuffix));
        return entry;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
