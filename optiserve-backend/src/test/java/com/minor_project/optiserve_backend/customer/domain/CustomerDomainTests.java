package com.minor_project.optiserve_backend.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import org.junit.jupiter.api.Test;

class CustomerDomainTests {

    @Test
    void createsCustomerLinkedToEnabledUser() {
        AuthUser user = AuthUser.create("operator@example.com", "encoded-password");
        Customer customer = Customer.create(user, "  Alex Customer  ", null);

        assertThat(customer.getUser()).isSameAs(user);
        assertThat(customer.getName()).isEqualTo("Alex Customer");
        assertThat(customer.getPhone()).isNull();
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void requiresAUserAndNonBlankName() {
        AuthUser user = AuthUser.create("operator@example.com", "encoded-password");

        assertThatThrownBy(() -> Customer.create(null, "Alex", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("user must not be null");
        assertThatThrownBy(() -> Customer.create(user, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }
}
