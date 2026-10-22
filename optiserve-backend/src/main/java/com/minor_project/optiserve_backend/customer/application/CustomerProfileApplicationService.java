package com.minor_project.optiserve_backend.customer.application;

import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.customer.api.CustomerProfileRequest;
import com.minor_project.optiserve_backend.customer.api.CustomerProfileResponse;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileApplicationService {

    private final CustomerRepository customerRepository;
    private final AuthUserRepository authUserRepository;
    private final VehicleRepository vehicleRepository;

    public CustomerProfileApplicationService(
            CustomerRepository customerRepository,
            AuthUserRepository authUserRepository,
            VehicleRepository vehicleRepository) {
        this.customerRepository = customerRepository;
        this.authUserRepository = authUserRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse findMine(String email) {
        return toResponse(findMineEntity(email));
    }

    @Transactional
    public CustomerProfileResponse saveMine(String email, CustomerProfileRequest request) {
        Customer customer = customerRepository.findByUser_EmailIgnoreCase(email)
                .orElseGet(() -> authUserRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                        .map(user -> Customer.create(user, request.name(), request.phone()))
                        .orElseThrow(() -> new ResourceNotFoundException("Account was not found.")));
        if (customer.getId() != null) {
            customer.updateProfile(request.name(), request.phone());
        }
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void deleteMine(String email) {
        Customer customer = findMineEntity(email);
        if (vehicleRepository.existsByCustomer_Id(customer.getId())) {
            throw new ConflictException("Customer profile cannot be deleted while vehicles are registered.");
        }
        customerRepository.delete(customer);
    }

    private Customer findMineEntity(String email) {
        return customerRepository.findByUser_EmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile was not found."));
    }

    private static CustomerProfileResponse toResponse(Customer customer) {
        return new CustomerProfileResponse(customer.getId(), customer.getName(), customer.getPhone());
    }
}
