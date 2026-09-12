package com.minor_project.optiserve_backend.Services;

import com.minor_project.optiserve_backend.operations.domain.Customer;
import com.minor_project.optiserve_backend.operations.persistence.CustomerRepository;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class Login_service {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public Login_service(
            AuthenticationManager authenticationManager,
            CustomerRepository customerRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.customerRepository = customerRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public Customer save(@Valid Customer customer) {
        if (customer.getPassword() != null && !customer.getPassword().isBlank()) {
            customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        }
        if (customer.getRole() == null || customer.getRole().isBlank()) {
            customer.setRole("ROLE_CUSTOMER");
        }
        return customerRepository.save(customer);
    }

    public Map<String, String> verify(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        if (authentication.isAuthenticated()) {
            Customer customer = customerRepository.findByName(username)
                    .or(() -> customerRepository.findByEmail(username))
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            String role = customer.getRole() != null ? customer.getRole() : "ROLE_CUSTOMER";
            String token = jwtService.generateToken(customer.getName(), role);

            return Map.of(
                    "token", token,
                    "username", customer.getName(),
                    "role", role
            );
        } else {
            throw new RuntimeException("Authentication failed");
        }
    }
}
