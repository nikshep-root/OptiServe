package com.minor_project.optiserve_backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleDomainTests {

    @Test
    void vehicleNormalizesRegistrationAndTrimsDescriptiveFields() {
        UUID customerId = UUID.randomUUID();

        Vehicle vehicle = Vehicle.create(customerId, " ka 01 ab 1234 ", " Toyota ", " Camry ", 2024);

        assertThat(vehicle.getCustomerId()).isEqualTo(customerId);
        assertThat(vehicle.getRegistrationNumber()).isEqualTo("KA 01 AB 1234");
        assertThat(vehicle.getMake()).isEqualTo("Toyota");
        assertThat(vehicle.getModel()).isEqualTo("Camry");
        assertThat(vehicle.getYear()).isEqualTo(2024);
    }

    @Test
    void vehicleRejectsMissingOrInvalidRequiredValues() {
        UUID customerId = UUID.randomUUID();

        assertThatNullPointerException()
                .isThrownBy(() -> Vehicle.create(null, "KA01AB1234", "Toyota", "Camry", 2024));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Vehicle.create(customerId, " ", "Toyota", "Camry", 2024));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Vehicle.create(customerId, "KA01AB1234", "", "Camry", 2024));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Vehicle.create(customerId, "KA01AB1234", "Toyota", "Camry", 0));
    }
}
