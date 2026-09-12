package com.minor_project.optiserve_backend.operations.persistence;

import com.minor_project.optiserve_backend.operations.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<Customer> findByName(String name);
}
