package com.minor_project.optiserve_backend.Services;

import com.minor_project.optiserve_backend.operations.domain.Customer;
import com.minor_project.optiserve_backend.operations.persistence.CustomerRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailServices implements UserDetailsService {

    private final CustomerRepository repo;

    public UserDetailServices(CustomerRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // Allow lookup by name or by email
        Customer customer = repo.findByName(identifier)
                .or(() -> repo.findByEmail(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        String role = customer.getRole() != null && !customer.getRole().isBlank()
                ? customer.getRole()
                : "ROLE_CUSTOMER";

        return User.builder()
                .username(customer.getName())
                .password(customer.getPassword() != null ? customer.getPassword() : "")
                .authorities(role)
                .build();
    }
}
