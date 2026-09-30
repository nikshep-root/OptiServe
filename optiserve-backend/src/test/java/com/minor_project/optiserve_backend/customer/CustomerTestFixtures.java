package com.minor_project.optiserve_backend.customer;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import java.util.UUID;

public final class CustomerTestFixtures {

    private CustomerTestFixtures() {
    }

    public static Customer createPersistedCustomer(
            AuthUserRepository users, CustomerRepository customers) {
        String suffix = UUID.randomUUID().toString();
        AuthUser user = users.saveAndFlush(AuthUser.create("customer-" + suffix + "@example.com", "test-hash"));
        return customers.saveAndFlush(Customer.create(user, "Test Customer", null));
    }
}
