package com.minor_project.optiserve_backend.customer.api;

import com.minor_project.optiserve_backend.customer.application.CustomerProfileApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/me")
public class CustomerProfileController {

    private final CustomerProfileApplicationService customerProfileApplicationService;

    public CustomerProfileController(CustomerProfileApplicationService customerProfileApplicationService) {
        this.customerProfileApplicationService = customerProfileApplicationService;
    }

    @GetMapping
    public CustomerProfileResponse findMine(Authentication authentication) {
        return customerProfileApplicationService.findMine(authentication.getName());
    }

    @PutMapping
    public CustomerProfileResponse saveMine(
            Authentication authentication, @Valid @RequestBody CustomerProfileRequest request) {
        return customerProfileApplicationService.saveMine(authentication.getName(), request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMine(Authentication authentication) {
        customerProfileApplicationService.deleteMine(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
