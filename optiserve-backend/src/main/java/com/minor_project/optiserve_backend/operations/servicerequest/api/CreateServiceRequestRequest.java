package com.minor_project.optiserve_backend.operations.servicerequest.api;

import com.minor_project.optiserve_backend.operations.domain.PriorityClass;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateServiceRequestRequest(
        @NotNull(message = "vehicleId is required")
        UUID vehicleId,
        @NotNull(message = "priority is required")
        PriorityClass priority,
        Instant appointmentTime,
        @NotEmpty(message = "serviceTypeIds must contain at least one stage")
        List<@NotNull(message = "serviceTypeIds must not contain null values") UUID> serviceTypeIds) {

    @AssertTrue(message = "appointmentTime is required for APPOINTMENT priority")
    public boolean hasAppointmentTimeWhenRequired() {
        return priority != PriorityClass.APPOINTMENT || appointmentTime != null;
    }
}
