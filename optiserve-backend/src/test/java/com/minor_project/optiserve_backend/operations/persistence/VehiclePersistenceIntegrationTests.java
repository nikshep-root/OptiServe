package com.minor_project.optiserve_backend.operations.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class VehiclePersistenceIntegrationTests {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV5AndHibernatePersistVehicle() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024));

        assertThat(vehicleRepository.findById(vehicle.getId()))
                .isPresent()
                .get()
                .extracting(Vehicle::getRegistrationNumber, Vehicle::getMake, Vehicle::getModel, Vehicle::getYear)
                .containsExactly("KA01AB1234", "Toyota", "Camry", 2024);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vehicles'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationNumberUniquenessIsEnforced() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(UUID.randomUUID(), "KA01AB1234", "Toyota", "Camry", 2024));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO vehicles (id, customer_id, registration_number, make, model, year, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), vehicle.getRegistrationNumber(), "Honda", "City", 2023,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
