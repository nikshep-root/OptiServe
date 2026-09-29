package com.minor_project.optiserve_backend.customer.domain;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AuthUser user;

    @NotBlank
    @Size(max = 200)
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 32)
    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    private Customer(AuthUser user, String name, String phone) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.name = requireText(name);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Customer create(AuthUser user, String name, String phone) {
        return new Customer(user, name, phone);
    }

    public static Customer reference(UUID id) {
        Customer customer = new Customer();
        customer.id = Objects.requireNonNull(id, "id must not be null");
        return customer;
    }

    public UUID getId() {
        return id;
    }

    public AuthUser getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
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

    private static String requireText(String value) {
        Objects.requireNonNull(value, "name must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("name must not exceed 200 characters");
        }
        return normalized;
    }
}
