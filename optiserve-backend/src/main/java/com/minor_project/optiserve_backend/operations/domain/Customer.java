package com.minor_project.optiserve_backend.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Data
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "phone", unique = true, length = 32)
    private String phone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    private Customer(String name, String email, String password, String phone, String address, String role) {
        this.name = requireText(name, "name");
        this.email = email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
        this.password = password != null && !password.isBlank() ? password : null;
        this.phone = phone != null && !phone.isBlank() ? phone.trim() : null;
        this.address = address != null && !address.isBlank() ? address.trim() : null;
        this.role = role != null && !role.isBlank() ? role.trim() : "ROLE_CUSTOMER";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Customer createRegistered(String name, String email, String password, String phone, String address) {
        requireText(email, "email");
        requireText(password, "password");
        return new Customer(name, email, password, phone, address, "ROLE_CUSTOMER");
    }

    public static Customer createAdmin(String name, String email, String password, String phone, String address) {
        requireText(email, "email");
        requireText(password, "password");
        return new Customer(name, email, password, phone, address, "ROLE_ADMIN");
    }

    public static Customer createGuest(String name, String phone) {
        return new Customer(name, null, null, phone, null, "ROLE_CUSTOMER");
    }

    public void updateContactInfo(String email, String phone, String address) {
        this.email = email != null && !email.isBlank() ? email.trim().toLowerCase() : null;
        this.phone = phone != null && !phone.isBlank() ? phone.trim() : null;
        this.address = address != null && !address.isBlank() ? address.trim() : null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role != null && !role.isBlank() ? role.trim() : "ROLE_CUSTOMER";
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void initializeTimestamps() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
