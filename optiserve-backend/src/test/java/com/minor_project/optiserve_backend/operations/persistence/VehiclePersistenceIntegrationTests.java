package com.minor_project.optiserve_backend.operations.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.customer.CustomerTestFixtures;
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
    private AuthUserRepository users;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV8AndHibernatePersistCustomerVehicleRelationship() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(customer(), "KA01AB1234", "Toyota", "Camry", 2024));

        assertThat(vehicleRepository.findById(vehicle.getId()))
                .isPresent()
                .get()
                .satisfies(persistedVehicle -> {
                    assertThat(persistedVehicle.getRegistrationNumber()).isEqualTo("KA01AB1234");
                    assertThat(persistedVehicle.getMake()).isEqualTo("Toyota");
                    assertThat(persistedVehicle.getModel()).isEqualTo("Camry");
                    assertThat(persistedVehicle.getYear()).isEqualTo(2024);
                    assertThat(persistedVehicle.getCustomer().getUser().getUsername())
                            .contains("@example.com");
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('vehicles', 'customers', 'users')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void registrationNumberUniquenessIsEnforced() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(
                Vehicle.create(customer(), "KA01AB1234", "Toyota", "Camry", 2024));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO vehicles (id, customer_id, registration_number, make, model, year, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), vehicle.getCustomerId(), vehicle.getRegistrationNumber(), "Honda", "City", 2023,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void vehicleCustomerForeignKeyRejectsUnknownCustomer() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO vehicles (id, customer_id, registration_number, make, model, year, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "KA01AB9999", "Honda", "City", 2023,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userCanHaveOnlyOneCustomerProfile() {
        Customer customer = customer();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO customers (id, user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), customer.getUser().getId(), "Duplicate profile",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Customer customer() {
        return CustomerTestFixtures.createPersistedCustomer(users, customers);
    }
}
