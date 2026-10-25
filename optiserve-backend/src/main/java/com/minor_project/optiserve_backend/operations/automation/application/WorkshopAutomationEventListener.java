package com.minor_project.optiserve_backend.operations.automation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkshopAutomationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkshopAutomationEventListener.class);

    private final WorkshopAutomationCoordinator coordinator;

    public WorkshopAutomationEventListener(WorkshopAutomationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAutomationRequested(WorkshopAutomationRequested event) {
        try {
            coordinator.reconcile();
        } catch (RuntimeException exception) {
            LOGGER.error("Workshop automation reconciliation failed; eligible work remains available for a later trigger",
                    exception);
        }
    }
}
