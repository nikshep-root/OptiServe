package com.minor_project.optiserve_backend.authentication.persistence;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
